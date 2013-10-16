package hydna;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *  This class is used internally by the Channel class.
 *  A user of the library should not create an instance of this class.
 */
public class Connection implements Runnable {

    private static Map<String, Connection> m_availableConnections = new HashMap<String, Connection>();
    private static Lock m_connectionMutex = new ReentrantLock();

    private Lock m_destroyingMutex = new ReentrantLock();
    private Lock m_closingMutex = new ReentrantLock();
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
    private BufferedReader m_inStreamReader;

    private Map<Integer, OpenRequest> m_pendingOpenRequests;
    private Map<Integer, Channel> m_openChannels;

    private int m_channelRefCount = 0;

    private Thread m_listeningThread;


    /**
     *  Return an available connection or create a new one.
     *
     *  @param host The host associated with the connection.
     *  @param port The port associated with the connection.
     *  @return The connection.
     */
    public static Connection getConnection(String host, short port) {
        Connection connection;
        String ports = Short.toString(port);
        String key = host + ports;
      
        m_connectionMutex.lock();
        if (m_availableConnections.containsKey(key)) {
            connection = m_availableConnections.get(key);
        } else {
            connection = new Connection(host, port);
            m_availableConnections.put(key, connection);
        }
        m_connectionMutex.unlock();

        return connection;
    }
	
    /**
     *  Initializes a new Channel instance.
     *
     *  @param host The host the connection should connect to.
     *  @param port The port the connection should connect to.
     */
    public Connection(String host, short port) {
        m_host = host;
        m_port = port;

        m_openChannels = new ConcurrentHashMap<Integer, Channel>();
        m_pendingOpenRequests = new HashMap<Integer, OpenRequest>();
    }
	
    /**
     *  Returns the handshake status of the connection.
     *
     *  @return True if the connection has handshaked.
     */
    public boolean hasHandShaked() {
        return m_handshaked;
    }
	
    /**
     * Method to keep track of the number of channels that is associated 
     * with this connection instance.
     */
    synchronized void allocChannel() {
        m_channelRefCount++;
        
        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection", 0, "Allocating a new channel, channel ref count is " + m_channelRefCount);
        }
    }
	
    /**
     *  Decrease the reference count.
     *
     *  @param addr The channel to dealloc.
     */
    synchronized void deallocChannel(int ch) {
        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection", ch, "Deallocating a channel");
        }
		
        m_destroyingMutex.lock();
        m_closingMutex.lock();
        if (!m_destroying && !m_closing) {
            m_closingMutex.unlock();
            m_destroyingMutex.unlock();

            m_openChannels.remove(ch);
            
            if (HydnaDebug.HYDNADEBUG) {
                DebugHelper.debugPrint("Connection", ch, "Size of openSteams is now " + m_openChannels.size());
            }
        } else  {
            m_closingMutex.unlock();
            m_destroyingMutex.unlock();
        }
      
        --m_channelRefCount;

        checkRefCount();
    }
	
    /**
     *  Check if there are any more references to the connection.
     */
    private synchronized void checkRefCount() {
        if (m_channelRefCount == 0) {
            if (HydnaDebug.HYDNADEBUG) {
                DebugHelper.debugPrint("Connection", 0, "No more refs, destroy connection");
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
        }
    }


    /**
     *  Request to open a channel.
     *
     *  @param request The request to open the channel.
     *  @return True if request went well, else false.
     */
    boolean requestOpen(OpenRequest request) {
        int chcomp = request.getChannelId();
        Queue<OpenRequest> queue;
        OpenRequest currentRequest;
        Channel channel;

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection", chcomp, "A channel is trying to send a new open request");
        }

        if ((channel = m_openChannels.get(chcomp)) != null) {
            if (HydnaDebug.HYDNADEBUG) {
                DebugHelper.debugPrint("Connection", chcomp, "The channel was already open, cancel the open request");
            }
            return channel.setPendingOpenRequest(request);
        }

        if ((currentRequest = m_pendingOpenRequests.get(chcomp)) != null) {
            if (HydnaDebug.HYDNADEBUG) {
                DebugHelper.debugPrint("Connection", chcomp, "A open request is waiting to be sent, queue up the new open request");
            }
            return currentRequest.getChannel().setPendingOpenRequest(request);
        }

        m_pendingOpenRequests.put(chcomp, request);

        if (!m_connecting) {
            m_connecting = true;
            connectConnection(m_host, m_port);
            return true;
        }

        if (m_handshaked) {
            writeBytes(request.getFrame());
            request.setSent(true);
        }

        return true;
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
      
        if (request.isSent()) {
            return false;
        }
      
        m_pendingOpenRequests.remove(channelcomp);
      
        return true;
    }
	
    /**
     *  Connect the connection.
     *
     *  @param host The host to connect to.
     *  @param port The port to connect to.
     */
    private void connectConnection(String host, int port) {
		
        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection", 0, "Connecting, attempt ");
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
            m_inStreamReader = new BufferedReader(new InputStreamReader(m_socket.getInputStream()));
        	
            if (HydnaDebug.HYDNADEBUG) {
                DebugHelper.debugPrint("Connection", 0, "Connected, sending HTTP upgrade request");
            }
        	
            m_connected = true;
        	
            connectHandler();
        } catch (UnresolvedAddressException e) {
            destroy(new ChannelError("The host \"" + host + "\" could not be resolved"));
        } catch (IOException e) {
            destroy(new ChannelError("Could not connect to the host \"" + host + "\" on the port " + port));
        }
    }
	
    /**
     *  Send HTTP upgrade request.
     */
    private void connectHandler() {
        boolean success = false;

        try {
            m_outStream.writeBytes("GET / HTTP/1.1\r\n" +
                                   "Connection: upgrade\r\n" +
                                   "Upgrade: winksock/1\r\n" +
                                   "Host: " + m_host);

            m_outStream.writeBytes("\r\n\r\n");
            success = true;
        } catch (IOException e) {
            success = false;
        }

        if (!success) {
            destroy(new ChannelError("Could not send upgrade request"));
        } else {
            handshakeHandler();
        }
    }
	
    /**
     *  Handle the Handshake response frame.
     */
    private void handshakeHandler() {
        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection", 0, "Incoming upgrade response");
        }
        
        boolean fieldsLeft = true;
        boolean gotResponse = false;
        
        while (fieldsLeft) {
            String line;
        	
            try {
                line = m_inStreamReader.readLine();
                if (line.length() == 0) {
                    fieldsLeft = false;
                }
            } catch (IOException e) {
                destroy(new ChannelError("Server responded with bad handshake"));
                return;
            }
        	
            if (fieldsLeft) {
                // First line i a response, all others are fields
                if (!gotResponse) {
                    int code = 0;
                    int pos1, pos2;
        			
                    // Take the response code from "HTTP/1.1 101
                    // Switching Protocols"
                    pos1 = line.indexOf(" ");
                    if (pos1 != -1) {
                        pos2 = line.indexOf(" ", pos1 + 1);
        				
                        if (pos2 != -1) {
                            try {
                                code = Integer.parseInt(line.substring(pos1 + 1, pos2));
                            } catch (NumberFormatException e) {
                                destroy(new ChannelError("Could not read the status from the response \"" + line + "\""));
                            }
                        }
                    }
        			
                    switch (code) {
                        case 101:
                        // Everything is ok, continue.
                        break;
                        default:
                        destroy(new ChannelError("Server responded with bad HTTP response code, " + code));
                        break;
                    }
        			
                    gotResponse = true;
                } else {
                    line = line.toLowerCase();
                    int pos;

                    pos = line.indexOf("upgrade: ");
                    if (pos != -1) {
                        String header = line.substring(9);
                        if (!header.equals("winksock/1")) {
                            destroy(new ChannelError("Bad protocol version: " + header));
                            return;
                        }
                    }
                }
            }
        }

        m_handshaked = true;
        m_connecting = false;

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection", 0, "Handshake done on connection");
        }

        for (OpenRequest request : m_pendingOpenRequests.values()) {
            writeBytes(request.getFrame());

            if (m_connected) {
                request.setSent(true);
                if (HydnaDebug.HYDNADEBUG) {
                    DebugHelper.debugPrint("Connection", request.getChannelId(), "Open request sent");
                }
            } else {
                return;
            }
        }

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection", 0, "Creating a new thread for frame listening");
        }

        try {
            m_listeningThread = new Thread(this);
            m_listeningThread.start();
        } catch (IllegalThreadStateException e) {
            destroy(new ChannelError("Could not create a new thread for frame listening"));
            return;
        }
    }
	
    /**
     * The method that is called in the new thread.
     * Listens for incoming frames.
     */
    public void run() {
        receiveHandler();
    }
	
    /**
     *  Handles all incoming data.
     */
    public void receiveHandler() {
        int size;
        int headerSize = Frame.HEADER_SIZE;
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
                    destroy(new ChannelError("Could not read from the connection"));
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
                    destroy(new ChannelError("Could not read from the connection"));
                } else {
                    m_listeningMutex.unlock();
                }
                break;
            }

            payload.flip();
            
            ch = header.getInt();
            byte of = header.get();
            op   = of >> 3 & 3;
            flag = of & 7;

            switch (op) {

                case Frame.OPEN:
                if (HydnaDebug.HYDNADEBUG) {
                    DebugHelper.debugPrint("Connection", ch, "Received open response");
                }
                processOpenFrame(ch, flag, payload);
                break;

                case Frame.DATA:
                if (HydnaDebug.HYDNADEBUG) {
                    DebugHelper.debugPrint("Connection", ch, "Received data");
                }
                processDataFrame(ch, flag, payload);
                break;

                case Frame.SIGNAL:
                if (HydnaDebug.HYDNADEBUG) {
                    DebugHelper.debugPrint("Connection", ch, "Received signal");
                }
                processSignalFrame(ch, flag, payload);
                break;
            }

            offset = 0;
            n = 1;
            header.clear();
        }
        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection", 0, "Listening thread exited");
        }
    }
	
    /**
     *  Process an open frame.
     *
     *  @param addr The address that should receive the open frame.
     *  @param errcode The error code of the open frame.
     *  @param payload The content of the open frame.
     */
    private void processOpenFrame(int ch, int errcode, ByteBuffer payload) {
        OpenRequest request;
        Channel channel;
        int respch = 0;
        String message = "";
        
        request = m_pendingOpenRequests.get(ch);

        if (request == null) {
            destroy(new ChannelError("The server sent a invalid open frame"));
            return;
        }

        channel = request.getChannel();

        if (errcode == Frame.OPEN_ALLOW) {
            respch = ch;
            
            if (payload != null) {
                Charset charset = Charset.forName("US-ASCII");
                CharsetDecoder decoder = charset.newDecoder();
            	
                try {
                    message = decoder.decode(payload).toString();
                } catch (CharacterCodingException e) {
                }
            }
        } else {
            m_pendingOpenRequests.remove(ch);

            String m = "";
            if (payload != null && payload.capacity() > 0) {
                Charset charset = Charset.forName("US-ASCII");
                CharsetDecoder decoder = charset.newDecoder();
                try {
                    m = decoder.decode(payload).toString();
                } catch (CharacterCodingException e) {}
            }

            if (HydnaDebug.HYDNADEBUG) {
                DebugHelper.debugPrint("Connection", ch, "The server rejected the open request, errorcode " + errcode);
            }

            ChannelError error = ChannelError.fromOpenError(errcode, m);
            channel.destroy(error);
            return;
        }

        m_openChannels.put(respch, channel);

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection", respch, "A new channel was added");
            DebugHelper.debugPrint("Connection", respch, "The size of openChannels is now " + m_openChannels.size());
        }

        channel.openSuccess(respch, message);

        m_pendingOpenRequests.remove(ch);
    }
	
    /**
     *  Process a data frame.
     *
     *  @param addr The address that should receive the data.
     *  @param priority The priority of the data.
     *  @param payload The content of the data.
     */
    private void processDataFrame(int ch, int priority, ByteBuffer payload) {
        Channel channel = null;
        ChannelData data;

        channel = m_openChannels.get(ch);

        if (channel == null) {
            destroy(new ChannelError("No channel was available to take care of the data received"));
            return;
        }

        if (payload == null || payload.capacity() == 0) {
            destroy(new ChannelError("Zero data frame received"));
            return;
        }

        data = new ChannelData(priority, payload);
        channel.addData(data);
    }
	
    /**
     *  Process a signal frame.
     *
     *  @param channel The channel that should receive the signal.
     *  @param flag The flag of the signal.
     *  @param payload The content of the signal.
     *  @return False is something went wrong.
     */
    private boolean processSignalFrame(Channel channel,
                                       int flag,
                                       ByteBuffer payload) {
        ChannelSignal signal;

        if (flag != Frame.SIG_EMIT) {
            String m = "";
            if (payload != null && payload.capacity() > 0) {
                Charset charset = Charset.forName("US-ASCII");
                CharsetDecoder decoder = charset.newDecoder();
	
                try {
                    m = decoder.decode(payload).toString();
                } catch (CharacterCodingException e) {}
            }
            ChannelError error = new ChannelError("", 0x0);

            if (flag != Frame.SIG_END) {
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
     *  Process a signal frame.
     *
     *  @param addr The address that should receive the signal.
     *  @param flag The flag of the signal.
     *  @param payload The content of the signal.
     */
    private void processSignalFrame(int ch, int flag, ByteBuffer payload) {
        if (ch == 0) {
            boolean destroying = false;
            int size = payload.capacity();

            if (flag != Frame.SIG_EMIT || payload == null || size == 0) {
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

                if (!processSignalFrame(channel, flag, payloadCopy)) {
                    it.remove();
                }
            }

            if (destroying) {
                m_closingMutex.lock();
                m_closing = false;
                m_closingMutex.unlock();

                checkRefCount();
            }
        } else {
            Channel channel = null;

            channel = m_openChannels.get(ch);

            if (channel == null) {
                destroy(new ChannelError("Received unknown channel"));
                return;
            }

            if (flag != Frame.SIG_EMIT && !channel.isClosing()) {
                Frame frame = new Frame(ch, Frame.SIGNAL, Frame.SIG_END, payload);
                writeBytes(frame);

                return;
            }

            processSignalFrame(channel, flag, payload);
        }
    }
	
    /**
     *  Destroy the connection.
     *
     *  @error The cause of the destroy.
     */
    private void destroy(ChannelError error) {
        m_destroyingMutex.lock();
        m_destroying = true;
        m_destroyingMutex.unlock();

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection",0, "Destroying connection because: " + error.getMessage());
        }

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection", 0, "Destroying pendingOpenRequests of size " + m_pendingOpenRequests.size());
        }

        for (OpenRequest request : m_pendingOpenRequests.values()) {
            if (HydnaDebug.HYDNADEBUG) {
                DebugHelper.debugPrint("Connection", request.getChannelId(), "Destroying channel");
            }
            request.getChannel().destroy(error);
        }
        m_pendingOpenRequests.clear();

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection", 0, "Destroying openChannels of size " + m_openChannels.size());
        }
        for (Channel channel : m_openChannels.values()) {
            if (HydnaDebug.HYDNADEBUG) {
                DebugHelper.debugPrint("Connection", channel.getChannel(), "Destroying channel");
            }
            channel.destroy(error);
        }				
        m_openChannels.clear();


        if (m_connected) {
            if (HydnaDebug.HYDNADEBUG) {
                DebugHelper.debugPrint("Connection", 0, "Closing connection");
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

        m_connectionMutex.lock();
        if (m_availableConnections.containsKey(key)) {
            m_availableConnections.remove(key);
        }
        m_connectionMutex.unlock();

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection", 0, "Destroying connection done");
        }

        m_destroyingMutex.lock();
        m_destroying = false;
        m_destroyingMutex.unlock();
    }
	
    /**
     *  Writes a frame to the connection.
     *
     *  @param frame The frame to be sent.
     *  @return True if the frame was sent.
     */
    public boolean writeBytes(Frame frame) {
        if (m_handshaked) {
            int n = -1;
            ByteBuffer data = frame.getData();
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
                destroy(new ChannelError("Could not write to the connection"));
                return false;
            }
            return true;
        }
        return false;
    }
}