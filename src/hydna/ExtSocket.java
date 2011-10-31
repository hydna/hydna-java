package hydna;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *  This class is used internally by the Channel class.
 *  A user of the library should not create an instance of this class.
 */
public class ExtSocket implements Runnable {
	private static final int HANDSHAKE_RESP_SIZE = 5;
	
	private static Map<String, ExtSocket> m_availableSockets = new HashMap<String, ExtSocket>();
	private static Lock m_socketMutex = new ReentrantLock();
	
	private Lock m_channelRefMutex = new ReentrantLock();
	private Lock m_destroyingMutex = new ReentrantLock();
	private Lock m_closingMutex = new ReentrantLock();
	private Lock m_openChannelsMutex = new ReentrantLock();
	private Lock m_openWaitMutex = new ReentrantLock();
	private Lock m_pendingMutex = new ReentrantLock();
	private Lock m_listeningMutex = new ReentrantLock();
	
	private boolean m_connecting = false;
	private boolean m_connected = false;
	private boolean m_handshaked = false;
	private boolean m_destroying = false;
	private boolean m_closing = false;
	private boolean m_listening = false;
	
	private String m_host;
	private short m_port;
	
	private SocketChannel m_socketChannel;
	private Socket m_socket;
	private DataOutputStream m_outStream;
	private DataInputStream m_inStream;
	
	private Map<Integer, OpenRequest> m_pendingOpenRequests = new HashMap<Integer, OpenRequest>();
	private Map<Integer, Channel> m_openChannels = new HashMap<Integer, Channel>();
	private Map<Integer, Queue<OpenRequest>> m_openWaitQueue = new HashMap<Integer, Queue<OpenRequest>>();
	
	private int m_channelRefCount = 0;
	
	private Thread m_listeningThread;
	
	/**
     *  Return an available socket or create a new one.
     *
     *  @param host The host associated with the socket.
     *  @param port The port associated with the socket.
     *  @return The socket.
     */
	public static ExtSocket getSocket(String host, short port) {
		ExtSocket socket;
        String ports = Short.toString(port);
        String key = host + ports;
      
        m_socketMutex.lock();
        if (m_availableSockets.containsKey(key)) {
            socket = m_availableSockets.get(key);
        } else {
            socket = new ExtSocket(host, port);
            m_availableSockets.put(key, socket);
        }
        m_socketMutex.unlock();

        return socket;
	}
	
	/**
     *  Initializes a new Channel instance.
     *
     *  @param host The host the socket should connect to.
     *  @param port The port the socket should connect to.
     */
	public ExtSocket(String host, short port) {
		m_host = host;
		m_port = port;
	}
	
	/**
     *  Returns the handshake status of the socket.
     *
     *  @return True if the socket has handshaked.
     */
	public boolean hasHandShaked() {
		return m_handshaked;
	}
	
	/**
     * Method to keep track of the number of channels that is associated 
     * with this socket instance.
     */
	public void allocChannel() {
		m_channelRefMutex.lock();
        m_channelRefCount++;
        m_channelRefMutex.unlock();
        
        if (HydnaDebug.HYDNADEBUG) {
        	DebugHelper.debugPrint("ExtSocket", 0, "Allocating a new channel, channel ref count is " + m_channelRefCount);
        }
	}
	
	/**
     *  Decrease the reference count.
     *
     *  @param addr The channel to dealloc.
     */
	public void deallocChannel(int ch) {
		if (HydnaDebug.HYDNADEBUG) {
			DebugHelper.debugPrint("ExtSocket", ch, "Deallocating a channel");
		}
		
        m_destroyingMutex.lock();
        m_closingMutex.lock();
        if (!m_destroying && !m_closing) {
            m_closingMutex.unlock();
            m_destroyingMutex.unlock();

            m_openChannelsMutex.lock();
            m_openChannels.remove(ch);
            
            if (HydnaDebug.HYDNADEBUG) {
            	DebugHelper.debugPrint("ExtSocket", ch, "Size of openSteams is now " + m_openChannels.size());
        	}
            m_openChannelsMutex.unlock();
        } else  {
            m_closingMutex.unlock();
            m_destroyingMutex.unlock();
        }
      
        m_channelRefMutex.lock();
        --m_channelRefCount;
        m_channelRefMutex.unlock();

        checkRefCount();
	}
	
	/**
     *  Check if there are any more references to the socket.
     */
	private void checkRefCount() {
		m_channelRefMutex.lock();
        if (m_channelRefCount == 0) {
            m_channelRefMutex.unlock();
            if (HydnaDebug.HYDNADEBUG) {
            	DebugHelper.debugPrint("ExtSocket", 0, "No more refs, destroy socket");
            }
            
            m_destroyingMutex.lock();
            m_closingMutex.lock();
            if (!m_destroying && !m_closing) {
                m_closingMutex.unlock();
                m_destroyingMutex.unlock();
                destroy(new ChannelError("", 0x0));
            } else {
                m_closingMutex.unlock();
                m_destroyingMutex.unlock();
            }
        } else {
            m_channelRefMutex.unlock();
        }
	}
	
	/**
     *  Request to open a channel.
     *
     *  @param request The request to open the channel.
     *  @return True if request went well, else false.
     */
	public boolean requestOpen(OpenRequest request) {
		int chcomp = request.getChannelId();
        Queue<OpenRequest> queue;

        if (HydnaDebug.HYDNADEBUG) {
        	DebugHelper.debugPrint("ExtSocket", chcomp, "A channel is trying to send a new open request");
        }

        m_openChannelsMutex.lock();
        if (m_openChannels.containsKey(chcomp)) {
            m_openChannelsMutex.unlock();
            
            if (HydnaDebug.HYDNADEBUG) {
            	DebugHelper.debugPrint("ExtSocket", chcomp, "The channel was already open, cancel the open request");
            }
            
            return false;
        }
        m_openChannelsMutex.unlock();

        m_pendingMutex.lock();
        if (m_pendingOpenRequests.containsKey(chcomp)) {
            m_pendingMutex.unlock();

            if (HydnaDebug.HYDNADEBUG) {
            	DebugHelper.debugPrint("ExtSocket", chcomp, "A open request is waiting to be sent, queue up the new open request");
            }
            
            m_openWaitMutex.lock();
            queue = m_openWaitQueue.get(chcomp);
        
            if (queue == null) {
            	queue = new LinkedList<OpenRequest>();
                m_openWaitQueue.put(chcomp, queue);
            } 
        
            queue.add(request);
            m_openWaitMutex.unlock();
        } else if (!m_handshaked) {
        	if (HydnaDebug.HYDNADEBUG) {
        		DebugHelper.debugPrint("ExtSocket", chcomp, "The socket was not connected, queue up the new open request");
        	}
            
        	m_pendingOpenRequests.put(chcomp, request);
            m_pendingMutex.unlock();
            
            if (!m_connecting) {
                m_connecting = true;
                connectSocket(m_host, m_port);
            }
        } else {
        	m_pendingOpenRequests.put(chcomp, request);
            m_pendingMutex.unlock();

            if (HydnaDebug.HYDNADEBUG) {
            	DebugHelper.debugPrint("ExtSocket", chcomp, "The socket was already connected, sending the new open request");
            }

            writeBytes(request.getPacket());
            request.setSent(true);
        }
      
        return m_connected;
	}
	
	/**
     *  Try to cancel an open request. Returns true on success else
     *  false.
     *
     *  @param request The request to cancel.
     *  @return True if the request was canceled.
     */
	public boolean cancelOpen(OpenRequest request) {
		int channelcomp = request.getChannelId();
        Queue<OpenRequest> queue;
        Queue<OpenRequest> tmp = new LinkedList<OpenRequest>();
        boolean found = false;
      
        if (request.isSent()) {
            return false;
        }
      
        m_openWaitMutex.lock();
        queue = m_openWaitQueue.get(channelcomp);
      
        m_pendingMutex.lock();
        if (m_pendingOpenRequests.containsKey(channelcomp)) {
            m_pendingOpenRequests.remove(channelcomp);
        
            if (queue != null && queue.size() > 0)  {
                m_pendingOpenRequests.put(channelcomp, queue.poll());
            }

            m_pendingMutex.unlock();
            m_openWaitMutex.unlock();
            return true;
        }
        m_pendingMutex.unlock();
      
        // Should not happen...
        if (queue == null) {
            m_openWaitMutex.unlock();
            return false;
        }
      
        while (!queue.isEmpty() && !found) {
            OpenRequest r = queue.poll();
            
            if (r == request) {
                found = true;
            } else {
                tmp.add(r);
            }
        }

        while(!tmp.isEmpty()) {
            OpenRequest r = tmp.poll();
            queue.add(r);
        }
        m_openWaitMutex.unlock();
      
        return found;
	}
	
	/**
     *  Writes a packet to the socket.
     *
     *  @param packet The packet to be sent.
     *  @return True if the packet was sent.
     */
	private void connectSocket(String host, int port) {
        if (HydnaDebug.HYDNADEBUG) {
        	DebugHelper.debugPrint("ExtSocket", 0, "Connecting socket");
        }
        
        try {
        	SocketAddress address = new InetSocketAddress(host, port);
            m_socketChannel = SocketChannel.open(address);
            m_socket = m_socketChannel.socket();
            
        	try {
        		m_socket.setTcpNoDelay(true);
        	} catch (SocketException e) {
            	System.err.println("WARNING: Could not set TCP_NODELAY");
            }
        	
        	m_outStream = new DataOutputStream(m_socket.getOutputStream());
        	m_inStream = new DataInputStream(m_socket.getInputStream());
        	
        	m_connected = true;
        	
        	connectHandler();
        } catch (UnresolvedAddressException e) {
        	destroy(new ChannelError("The host \"" + host + "\" could not be resolved"));
        } catch (IOException e) {
        	destroy(new ChannelError("Could not connect to the host \"" + host + "\""));
        }
	}
	
	/**
     *  Send a handshake packet.
     */
	private void connectHandler() {
		if (HydnaDebug.HYDNADEBUG) {
			DebugHelper.debugPrint("ExtSocket", 0, "Socket connected, sending handshake");
		}
		
        int length = m_host.length();
        boolean success = false;

        if (length < 256) {
        	try {
	            m_outStream.writeBytes("DNA1");
	            m_outStream.writeByte(length);
	            m_outStream.writeBytes(m_host);
	            
	            success = true;
        	} catch (IOException e) {
        		success = false;
        	}
        }

        if (!success) {
            destroy(new ChannelError("Could not send handshake"));
        } else {
            handshakeHandler();
        }
	}
	
	/**
     *  Handle the Handshake response packet.
     */
	private void handshakeHandler() {
		int responseCode = 0;
        int n = -1;
        byte data[] = new byte[HANDSHAKE_RESP_SIZE];
        String prefix = "DNA1";

        if (HydnaDebug.HYDNADEBUG) {
        	DebugHelper.debugPrint("ExtSocket", 0, "Incoming handshake response on socket");
        }
        
        try {
        	n = m_inStream.read(data, 0, HANDSHAKE_RESP_SIZE);
        } catch (IOException e) {
        	n = -1;
        }

        if (n != HANDSHAKE_RESP_SIZE) {
            destroy(new ChannelError("Server responded with bad handshake"));
            return;
        }

        responseCode = data[HANDSHAKE_RESP_SIZE - 1];
        data[HANDSHAKE_RESP_SIZE - 1] = '\0';
        
        if (!prefix.equals(new String(data, 0, HANDSHAKE_RESP_SIZE - 1, Charset.forName("US-ASCII")))) {
            destroy(new ChannelError("Server responded with bad handshake"));
            return;
        }

        if (responseCode > 0) {
            destroy(ChannelError.fromHandshakeError(responseCode));
            return;
        }

        m_handshaked = true;
        m_connecting = false;

        if (HydnaDebug.HYDNADEBUG) {
        	DebugHelper.debugPrint("ExtSocket", 0, "Handshake done on socket");
        }

        for (OpenRequest request : m_pendingOpenRequests.values()) {
            writeBytes(request.getPacket());

            if (m_connected) {
                request.setSent(true);
                if (HydnaDebug.HYDNADEBUG) {
                	DebugHelper.debugPrint("ExtSocket", request.getChannelId(), "Open request sent");
                }
            } else {
                return;
            }
        }

        if (HydnaDebug.HYDNADEBUG) {
        	DebugHelper.debugPrint("ExtSocket", 0, "Creating a new thread for packet listening");
        }

        try {
        m_listeningThread = new Thread(this);
        m_listeningThread.start();
        } catch (IllegalThreadStateException e) {
            destroy(new ChannelError("Could not create a new thread for packet listening"));
            return;
        }
	}
	
	/**
     * The method that is called in the new thread.
     * Listens for incoming packets.
     */
	public void run() {
		receiveHandler();
	}
	
	/**
     *  Handles all incoming data.
     */
	public void receiveHandler() {
		int size;
        int headerSize = Packet.HEADER_SIZE;
        int ch;
        int op;
        int flag;

        ByteBuffer header = ByteBuffer.allocate(headerSize);
        header.order(ByteOrder.BIG_ENDIAN);
        ByteBuffer payload;

        int offset = 0;
        int n = 1;

        m_listeningMutex.lock();
        m_listening = true;
        m_listeningMutex.unlock();

        for (;;) {
            try {
            	while(offset < headerSize && n >= 0) {
            		n = m_socketChannel.read(header);
                    offset += n;
                }
            } catch (Exception e) {
            	n = -1;
            }

            if (n <= 0) {
                m_listeningMutex.lock();
                if (m_listening) {
                    m_listeningMutex.unlock();
                    destroy(new ChannelError("Could not read from the socket"));
                } else {
                	m_listeningMutex.unlock();
                }
                break;
            }
            
            header.flip();

            size = (int)header.getShort() & 0xFFFF;
            payload = ByteBuffer.allocate(size - headerSize);
            payload.order(ByteOrder.BIG_ENDIAN);

            try {
            	while(offset < size && n >= 0) {
            		n = m_socketChannel.read(payload);
                    offset += n;
                }
            } catch (Exception e) {
            	n = -1;
            }

            if (n <= 0) {
                m_listeningMutex.lock();
                if (m_listening) {
                    m_listeningMutex.unlock();
                    destroy(new ChannelError("Could not read from the socket"));
                } else {
                	m_listeningMutex.unlock();
                }
                break;
            }

            payload.flip();
            
            header.get(); // Reserved
            ch = header.getInt();
            byte of = header.get();
            op   = of >> 4;
            flag = of & 0xf;

            switch (op) {

                case Packet.OPEN:
                	if (HydnaDebug.HYDNADEBUG) {
                		DebugHelper.debugPrint("ExtSocket", ch, "Received open response");
                	}
                    processOpenPacket(ch, flag, payload);
                    break;

                case Packet.DATA:
                	if (HydnaDebug.HYDNADEBUG) {
                		DebugHelper.debugPrint("ExtSocket", ch, "Received data");
                	}
                    processDataPacket(ch, flag, payload);
                    break;

                case Packet.SIGNAL:
                	if (HydnaDebug.HYDNADEBUG) {
                		DebugHelper.debugPrint("ExtSocket", ch, "Received signal");
                	}
                    processSignalPacket(ch, flag, payload);
                    break;
            }

            offset = 0;
            n = 1;
            header.clear();
        }
        if (HydnaDebug.HYDNADEBUG) {
        	DebugHelper.debugPrint("ExtSocket", 0, "Listening thread exited");
        }
	}
	
	/**
     *  Process an open packet.
     *
     *  @param addr The address that should receive the open packet.
     *  @param errcode The error code of the open packet.
     *  @param payload The content of the open packet.
     */
	private void processOpenPacket(int ch, int errcode, ByteBuffer payload) {
		OpenRequest request;
        Channel channel;
        int respch = 0;
        
        m_pendingMutex.lock();
        request = m_pendingOpenRequests.get(ch);
        m_pendingMutex.unlock();

        if (request == null) {
            destroy(new ChannelError("The server sent a invalid open packet"));
            return;
        }

        channel = request.getChannel();

        if (errcode == Packet.OPEN_SUCCESS) {
            respch = ch;
        } else if (errcode == Packet.OPEN_REDIRECT) {
            if (payload == null || payload.capacity() < 4) {
                destroy(new ChannelError("Expected redirect channel from the server"));
                return;
            }

            respch = payload.getInt();

            if (HydnaDebug.HYDNADEBUG) {
            	DebugHelper.debugPrint("ExtSocket",     ch, "Redirected from " + ch);
            	DebugHelper.debugPrint("ExtSocket", respch, "             to " + respch);
            }
        } else {
            m_pendingMutex.lock();
            m_pendingOpenRequests.remove(ch);
            m_pendingMutex.unlock();

            String m = "";
            if (payload != null && payload.capacity() > 0) {
            	Charset charset = Charset.forName("US-ASCII");
            	CharsetDecoder decoder = charset.newDecoder();
                try {
					m = decoder.decode(payload).toString();
				} catch (CharacterCodingException e) {}
            }

            if (HydnaDebug.HYDNADEBUG) {
            	DebugHelper.debugPrint("ExtSocket", ch, "The server rejected the open request, errorcode " + errcode);
            }

            ChannelError error = ChannelError.fromOpenError(errcode, m);
            channel.destroy(error);
            return;
        }

        m_openChannelsMutex.lock();
        if (m_openChannels.containsKey(respch)) {
            m_openChannelsMutex.unlock();
            destroy(new ChannelError("Server redirected to open channel"));
            return;
        }

        m_openChannels.put(respch, channel);
        if (HydnaDebug.HYDNADEBUG) {
        	DebugHelper.debugPrint("ExtSocket", respch, "A new channel was added");
        	DebugHelper.debugPrint("ExtSocket", respch, "The size of openChannels is now " + m_openChannels.size());
        }
        m_openChannelsMutex.unlock();

        channel.openSuccess(respch);

        m_openWaitMutex.lock();
        m_pendingMutex.lock();
        if (m_openWaitQueue.containsKey(ch)) {
            Queue<OpenRequest> queue = m_openWaitQueue.get(ch);
            
            if (queue != null)
            {
                // Destroy all pending request IF response wasn't a 
                // redirected channel.
                if (respch == ch) {
                    m_pendingOpenRequests.remove(ch);

                    ChannelError error = new ChannelError("Channel already open");

                    while (!queue.isEmpty()) {
                        request = queue.poll();
                        request.getChannel().destroy(error);
                    }

                    return;
                }

                request = queue.poll();
                m_pendingOpenRequests.put(ch, request);

                if (queue.isEmpty()) {
                    m_openWaitQueue.remove(ch);
                }

                writeBytes(request.getPacket());
                request.setSent(true);
            }
        } else {
            m_pendingOpenRequests.remove(ch);
        }
        m_pendingMutex.unlock();
        m_openWaitMutex.unlock();
	}
	
	/**
     *  Process a data packet.
     *
     *  @param addr The address that should receive the data.
     *  @param priority The priority of the data.
     *  @param payload The content of the data.
     */
	private void processDataPacket(int ch, int priority, ByteBuffer payload) {
		Channel channel = null;
        ChannelData data;
        
        m_openChannelsMutex.lock();
        if (m_openChannels.containsKey(ch))
            channel = m_openChannels.get(ch);
        m_openChannelsMutex.unlock();

        if (channel == null) {
            destroy(new ChannelError("No channel was available to take care of the data received"));
            return;
        }

        if (payload == null || payload.capacity() == 0) {
            destroy(new ChannelError("Zero data packet received"));
            return;
        }

        data = new ChannelData(priority, payload);
        channel.addData(data);
	}
	
	/**
     *  Process a signal packet.
     *
     *  @param channel The channel that should receive the signal.
     *  @param flag The flag of the signal.
     *  @param payload The content of the signal.
     *  @return False is something went wrong.
     */
	private boolean processSignalPacket(Channel channel, int flag, ByteBuffer payload) {
		ChannelSignal signal;

        if (flag > 0) {
            String m = "";
            if (payload != null && payload.capacity() > 0) {
            	Charset charset = Charset.forName("US-ASCII");
            	CharsetDecoder decoder = charset.newDecoder();
            	
                try {
					m = decoder.decode(payload).toString();
				} catch (CharacterCodingException e) {}
            }
            ChannelError error = new ChannelError("", 0x0);
            
            if (flag != Packet.SIG_END) {
                error = ChannelError.fromSigError(flag, m);
            }

            channel.destroy(error);
            return false;
        }

        if (channel == null)
            return false;

        signal = new ChannelSignal(flag, payload);
        channel.addSignal(signal);
        return true;
	}
	
	/**
     *  Process a signal packet.
     *
     *  @param addr The address that should receive the signal.
     *  @param flag The flag of the signal.
     *  @param payload The content of the signal.
     */
	private void processSignalPacket(int ch, int flag, ByteBuffer payload) {
		if (ch == 0) {
            m_openChannelsMutex.lock();
            boolean destroying = false;
            int size = payload.capacity();

            if (flag > 0x0 || payload == null || size == 0) {
                destroying = true;

                m_closingMutex.lock();
                m_closing = true;
                m_closingMutex.unlock();
            }
            
            Iterator<Channel> it = m_openChannels.values().iterator();
            while (it.hasNext()) {
            	Channel channel = it.next();
            	ByteBuffer payloadCopy = ByteBuffer.allocate(size);
            	payloadCopy.put(payload);
            	payloadCopy.flip();
            	payload.rewind();

                if (!destroying && channel == null) {
                    destroying = true;

                    m_closingMutex.lock();
                    m_closing = true;
                    m_closingMutex.unlock();
                }

                if (!processSignalPacket(channel, flag, payloadCopy)) {
                    it.remove();
                }
            }

            m_openChannelsMutex.unlock();

            if (destroying) {
                m_closingMutex.lock();
                m_closing = false;
                m_closingMutex.unlock();

                checkRefCount();
            }
        } else {
            m_openChannelsMutex.lock();
            Channel channel = null;

            if (m_openChannels.containsKey(ch))
                channel = m_openChannels.get(ch);

            if (channel == null) {
                m_openChannelsMutex.unlock();
                destroy(new ChannelError("Received unknown channel"));
                return;
            }
            
            if (flag > 0x0 && !channel.isClosing()) {
            	m_openChannelsMutex.unlock();
            	
            	Packet packet = new Packet(ch, Packet.SIGNAL, Packet.SIG_END, payload);
				writeBytes(packet);
				
				return;
			}

            processSignalPacket(channel, flag, payload);
            m_openChannelsMutex.unlock();
        }
	}
	
	/**
     *  Destroy the socket.
     *
     *  @error The cause of the destroy.
     */
	private void destroy(ChannelError error) {
		m_destroyingMutex.lock();
        m_destroying = true;
        m_destroyingMutex.unlock();

        if (HydnaDebug.HYDNADEBUG) {
        	DebugHelper.debugPrint("ExtSocket", 0, "Destroying socket because: " + error.getMessage());
        }

        m_pendingMutex.lock();
        if (HydnaDebug.HYDNADEBUG) {
        	DebugHelper.debugPrint("ExtSocket", 0, "Destroying pendingOpenRequests of size " + m_pendingOpenRequests.size());
        }
        
        for (OpenRequest request : m_pendingOpenRequests.values()) {
        	if (HydnaDebug.HYDNADEBUG) {
            	DebugHelper.debugPrint("ExtSocket", request.getChannelId(), "Destroying channel");
            }
			request.getChannel().destroy(error);
		}
        m_pendingOpenRequests.clear();
        m_pendingMutex.unlock();

        m_openWaitMutex.lock();
        if (HydnaDebug.HYDNADEBUG) {
        	DebugHelper.debugPrint("ExtSocket", 0, "Destroying waitQueue of size " + m_openWaitQueue.size());
        }
        for (Queue<OpenRequest> queue : m_openWaitQueue.values()) {
            while(queue != null && !queue.isEmpty()) {
                queue.poll().getChannel().destroy(error);
            }
        }
        m_openWaitQueue.clear();
        m_openWaitMutex.unlock();
        
        m_openChannelsMutex.lock();
        if (HydnaDebug.HYDNADEBUG) {
        	DebugHelper.debugPrint("ExtSocket", 0, "Destroying openChannels of size " + m_openChannels.size());
        }
        for (Channel channel : m_openChannels.values()) {
        	if (HydnaDebug.HYDNADEBUG) {
        		DebugHelper.debugPrint("ExtSocket", channel.getChannel(), "Destroying channel");
        	}
            channel.destroy(error);
        }				
        m_openChannels.clear();
        m_openChannelsMutex.unlock();

        if (m_connected) {
        	if (HydnaDebug.HYDNADEBUG) {
        		DebugHelper.debugPrint("ExtSocket", 0, "Closing socket");
        	}
            m_listeningMutex.lock();
            m_listening = false;
            m_listeningMutex.unlock();
            
            try {
				m_socketChannel.close();
			} catch (IOException e) {}
			
            m_connected = false;
            m_handshaked = false;
        }
        String ports = Short.toString(m_port);
        String key = m_host + ports;

        m_socketMutex.lock();
        if (m_availableSockets.containsKey(key)) {
            m_availableSockets.remove(key);
        }
        m_socketMutex.unlock();

        if (HydnaDebug.HYDNADEBUG) {
        	DebugHelper.debugPrint("ExtSocket", 0, "Destroying socket done");
        }
        
        m_destroyingMutex.lock();
        m_destroying = false;
        m_destroyingMutex.unlock();
	}
	
	/**
     *  Writes a packet to the socket.
     *
     *  @param packet The packet to be sent.
     *  @return True if the packet was sent.
     */
	public boolean writeBytes(Packet packet) {
		if (m_handshaked) {
            int n = -1;
            ByteBuffer data = packet.getData();
            int size = data.capacity();
            int offset = 0;

            try {
            	while(offset < size) {
                    n = m_socketChannel.write(data);
                    offset += n;
                }
            } catch (Exception e) {
            	n = -1;
            }

            if (n <= 0) {
                destroy(new ChannelError("Could not write to the socket"));
                return false;
            }
            return true;
        }
        return false;
	}
}
