package com.hydna;

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

    private static Map<String, Connection> m_availableConnections;

    private boolean m_connecting = false;
    private boolean m_connected = false;
    private boolean m_handshaked = false;
    private boolean m_destroying = false;
    private boolean m_listening = false;

    private String m_id;
    private String m_host;
    private short m_port;

    private SocketChannel m_socketChannel;
    private Socket m_socket;
    private DataOutputStream m_outStream;
    private BufferedReader m_inStreamReader;

    private Map<Integer, Channel> m_openChannels;

    private OpenRequest m_pendingOpenRequest;

    private int m_channelRefCount = 0;

    private Thread m_listeningThread;


    /**
     *  Return an available connection or create a new one.
     *
     *  @param host The host associated with the connection.
     *  @param port The port associated with the connection.
     *  @return The connection.
     */
    synchronized static Connection getConnection(String host, short port) {
        Connection connection;
        String id;

        id = host + Short.toString(port);

        if (m_availableConnections == null) {
            m_availableConnections = new HashMap<String, Connection>();
        }

        if (m_availableConnections.containsKey(id)) {
            connection = m_availableConnections.get(id);
        } else {
            connection = new Connection(id, host, port);
            m_availableConnections.put(id, connection);
        }

        return connection;
    }


    synchronized static void disposeConnection(Connection connection) {
        String id;
        synchronized (connection) {
            id = connection.m_id;
            if (id != null) {
                connection.m_id = null;
                if (m_availableConnections.containsKey(id)) {
                    m_availableConnections.remove(id);
                }
            }
        }
    }

    /**
     *  Initializes a new Channel instance.
     *
     *  @param host The host the connection should connect to.
     *  @param port The port the connection should connect to.
     */
    public Connection(String id, String host, short port) {
        m_id = id;
        m_host = host;
        m_port = port;

        m_openChannels = new ConcurrentHashMap<Integer, Channel>();
    }

    synchronized boolean isDestroying() {
        return m_destroying;
    }

    /**
     * Method to keep track of the number of channels that is associated 
     * with this connection instance.
     */
    void allocChannel() throws ChannelError {

        if (isDestroying()) {
            throw new ChannelError("Unable to alloc, connection is closing");
        }

        synchronized (this) {
            m_channelRefCount++;
        }

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection", 0,
                                   "Allocating a new channel," +
                                   "channel ref count is " +
                                   m_channelRefCount);
        }
    }
	
    /**
     *  Decrease the reference count.
     *
     *  @param channelPtr The channel to dealloc.
     */
    void deallocChannel(int channelPtr) {
        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection",
                                    channelPtr,
                                    "Deallocating a channel");
        }

        if (isDestroying()) {
            // Ignore if we are destroying.
            return;
        }

        m_openChannels.remove(channelPtr);

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection",
                                    channelPtr,
                                    "Size of openSteams is now "
                                         + m_openChannels.size());
        }

        --m_channelRefCount;

        checkRefCount();
    }
	
    /**
     *  Check if there are any more references to the connection.
     */
    private void checkRefCount() {

        synchronized (this) {

            if (HydnaDebug.HYDNADEBUG) {
                DebugHelper.debugPrint("Connection",
                                       0,
                                       "RefCount:" + m_channelRefCount);
            }

            if (m_channelRefCount != 0) {
                return;
            }            
        }

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection",
                                   0,
                                   "No more refs, destroy connection");
        }

        if (isDestroying() == false) {
            destroy(null);
        }
    }

    /**
     *  Request to open a channel.
     *
     *  @param request The request to open the channel.
     */
    void requestOpen(OpenRequest request) throws ChannelError {
        String path;

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection",
                                   0,
                                   "A channel is trying to send a new request");
        }

        path = request.getChannel().getPath();

        if (getChannelByPath(path) != null) {
            throw new ChannelError("Channel already open");
        }

        try {
            synchronized (this) {
                if (!m_handshaked) {
                    connectConnection(m_host, m_port);
                }
            }
        } catch (ChannelError e) {
            throw e;
        }

        synchronized (this) {
            m_pendingOpenRequest = request;
        }
    }

    private Channel getChannelByPath(String path) {
        Iterator<Channel> it;
        Channel channel;

        it = m_openChannels.values().iterator();
        while (it.hasNext()) {
            channel = it.next();
            if (channel.getPath() == path) {
                return channel;
            }
        }
        return null;
    }
	
    /**
     *  Connect the connection.
     *
     *  @param host The host to connect to.
     *  @param port The port to connect to.
     */
    private void connectConnection(String host, int port)
        throws ChannelError {
		
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
        	
            m_connecting = false;
            m_connected = true;
        	
            connectHandler();
        } catch (UnresolvedAddressException e) {
            m_connecting = false;
            throw new ChannelError("The host \"" + host + "\" could not be resolved");
        } catch (IOException e) {
            m_connecting = false;
            throw new ChannelError("Could not connect to the host \"" + host + "\" on the port " + port);
        }
    }
	
    /**
     *  Send HTTP upgrade request.
     */
    private void connectHandler() throws ChannelError {
        try {
            m_outStream.writeBytes("GET / HTTP/1.1\r\n" +
                                   "Connection: upgrade\r\n" +
                                   "Upgrade: winksock/1\r\n" +
                                   "Host: " + m_host);

            m_outStream.writeBytes("\r\n\r\n");
            handshakeHandler();
        } catch (IOException e) {
            m_connected = false;
            throw new ChannelError("Could not send upgrade request");
        } catch (ChannelError e) {
            m_connected = false;
            throw e;
        }
    }
	
    /**
     *  Handle the Handshake response frame.
     */
    private void handshakeHandler() throws ChannelError {
        ChannelError error;
        boolean fieldsLeft = true;
        boolean gotResponse = false;
        
        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection", 0, "Incoming upgrade response");
        }
                
        while (fieldsLeft) {
            String line;
        	
            try {
                line = m_inStreamReader.readLine();
                if (line.length() == 0) {
                    fieldsLeft = false;
                }
            } catch (IOException e) {
                error = new ChannelError("Server responded with bad handshake");
                throw error;
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
                                error = new ChannelError("Could not read " +
                                                         "the status from " +
                                                         "the response \"" +
                                                         line + "\"");
                                throw error;
                            }
                        }
                    }

                    if (code != 101) {
                        error = new ChannelError("Unexpected response " + 
                                                 "code, " + code);
                        throw error;
                    }

                    gotResponse = true;
                } else {
                    line = line.toLowerCase();
                    int pos;

                    pos = line.indexOf("upgrade: ");
                    if (pos != -1) {
                        String header = line.substring(9);
                        if (!header.equals("winksock/1")) {
                            error = new ChannelError("Bad protocol version: " +
                                                     header);
                            throw error;
                        }
                    }
                }
            }
        }

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection", 0, "Handshake done on connection");
        }

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection", 0, "Creating a new thread for frame listening");
        }

        try {
            m_listeningThread = new Thread(this);
            m_listeningThread.start();
        } catch (IllegalThreadStateException e) {
            error = new ChannelError("Could not create listening thread");
            throw error;
        }

        m_handshaked = true;
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
        int channelPtr;
        int flag;
        int ctype;
        int op;

        ByteBuffer header = ByteBuffer.allocate(Frame.HEADER_SIZE + 2);
        header.order(ByteOrder.BIG_ENDIAN);
        ByteBuffer data;

        int offset = 0;
        int n = 1;

        m_listening = true;

        for (;;) {
            try {
                while(offset < Frame.HEADER_SIZE + 2 && n >= 0) {
                    n = m_socketChannel.read(header);
                    offset += n;
                }
            } catch (Exception e) {
                n = -1;
            }

            if (n <= 0) {
                destroy(new ChannelError("Could not read from the connection"));
                break;
            }

            header.flip();

            size = (int)header.getShort() & 0xFFFF;
            data = ByteBuffer.allocate(size - Frame.HEADER_SIZE);
            data.order(ByteOrder.BIG_ENDIAN);

            try {
                while(offset < size + 2 && n >= 0) {
                    n = m_socketChannel.read(data);
                    offset += n;
                }
            } catch (Exception e) {
                n = -1;
            }

            if (n <= 0) {
                destroy(new ChannelError("Could not read from the connection"));
                break;
            }

            data.flip();
            
            channelPtr = header.getInt();
            byte of = header.get();

            ctype = (of & Frame.CTYPE_BITMASK) >> Frame.CTYPE_BITPOS;
            op = (of & Frame.OP_BITMASK) >> Frame.OP_BITPOS;
            flag = (of & Frame.FLAG_BITMASK);

            switch (op) {

                case Frame.KEEPALIVE:
                break;

                case Frame.OPEN:
                if (HydnaDebug.HYDNADEBUG) {
                    DebugHelper.debugPrint("Connection",
                                            channelPtr,
                                            "Received open response");
                }
                processOpenFrame(channelPtr, ctype, flag, data);
                break;

                case Frame.DATA:
                if (HydnaDebug.HYDNADEBUG) {
                    DebugHelper.debugPrint("Connection",
                                           channelPtr,
                                           "Received data");
                }
                processDataFrame(channelPtr, ctype, flag, data);
                break;

                case Frame.SIGNAL:
                if (HydnaDebug.HYDNADEBUG) {
                    DebugHelper.debugPrint("Connection",
                                           channelPtr,
                                           "Received signal");
                }
                processSignalFrame(channelPtr, ctype, flag, data);
                break;

                case Frame.RESOLVE:
                if (HydnaDebug.HYDNADEBUG) {
                    DebugHelper.debugPrint("Connection",
                                           channelPtr,
                                           "Received Resolve");
                }
                processResolveFrame(channelPtr, ctype, flag, data);
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
    private void processOpenFrame(int channelPtr,
                                  int ctype,
                                  int flag,
                                  ByteBuffer data) {
        OpenRequest request;
        Channel channel;

        synchronized (this) {
            request = m_pendingOpenRequest;
        }

        if (request == null) {
            destroy(new ChannelError("The server sent a invalid open frame"));
            return;
        }

        channel = request.getChannel();

        synchronized (this) {
            m_pendingOpenRequest = null;
        }

        if (flag == Frame.OPEN_ALLOW) {
            m_openChannels.put(channelPtr, channel);

            if (HydnaDebug.HYDNADEBUG) {
                DebugHelper.debugPrint("Connection", channelPtr, "A new channel was added");
                DebugHelper.debugPrint("Connection", channelPtr, "The size of openChannels is now " + m_openChannels.size());
            }

            channel.openSuccess(channelPtr, ctype, data);

            return;
        }

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection", channelPtr, "The server rejected the open request, errorcode " + flag);
        }

        ChannelError error = ChannelError.fromOpenError(flag, ctype, data);
        channel.destroy(error);
    }
	
    /**
     *  Process a data frame.
     *
     *  @param channelPtr The channel pointer that should receive the data.
     *  @param ctype The ContentType of the data.
     *  @param flag The flag of the data.
     *  @param data The data.
     */
    private void processDataFrame(int channelPtr,
                                  int ctype,
                                  int flag,
                                  ByteBuffer data) {
        Channel channel;
        ByteBuffer datac;
        Iterator<Channel> it;
        int size;

        if (data == null || data.capacity() == 0) {
            destroy(new ChannelError("Zero data frame received"));
            return;
        }

        size = data.capacity();

        if (channelPtr == 0) {
            it = m_openChannels.values().iterator();
            while (it.hasNext()) {
                channel = it.next();
                datac = ByteBuffer.allocate(size);
                datac.put(data);
                datac.flip();
                data.rewind();
                channel.addEvent(new ChannelData(channel, ctype, flag, datac));
            }

            return;   
        }

        channel = m_openChannels.get(channelPtr);

        if (channel == null) {
            destroy(new ChannelError("Invalid channel"));
            return;
        }

        channel.addEvent(new ChannelData(channel, ctype, flag, data));
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
                                       int ctype,
                                       int flag,
                                       ByteBuffer data) {
        ChannelSignal signal = null;
        ChannelError error = null;

        switch (flag) {

            case Frame.SIG_EMIT:
            signal = new ChannelSignal(channel, ctype, data);
            channel.addEvent(signal);
            return false;

            case Frame.SIG_END:
            signal = new ChannelEndSignal(channel, ctype, data);
            channel.destroy(signal);
            return true;

            default:
            error = ChannelError.fromSigError(flag, 0, null);
            channel.destroy(error);
            return true;

        }

    }
	
    /**
     *  Process a signal frame.
     *
     *  @param addr The address that should receive the signal.
     *  @param flag The flag of the signal.
     *  @param payload The content of the signal.
     */
    private void processSignalFrame(int channelPtr,
                                    int ctype,
                                    int flag,
                                    ByteBuffer data) {
        if (channelPtr == 0) {
            boolean destroying = false;
            int size = data.capacity();

            Iterator<Channel> it = m_openChannels.values().iterator();
            while (it.hasNext()) {
                Channel channel = it.next();
                ByteBuffer datac = ByteBuffer.allocate(size);
                datac.put(data);
                datac.flip();
                datac.rewind();

                if (processSignalFrame(channel, ctype, flag, datac) == false) {
                    it.remove();
                }
            }

            checkRefCount();
        } else {
            Channel channel = null;

            channel = m_openChannels.get(channelPtr);

            if (channel == null) {
                destroy(new ChannelError("Received unknown channel"));
                return;
            }

            processSignalFrame(channel, ctype, flag, data);
        }
    }

    private void processResolveFrame(int channelPtr,
                                     int ctype,
                                     int flag,
                                     ByteBuffer data) {
        OpenRequest request;
        Channel channel;
        ChannelError error;
        ByteBuffer path;

         synchronized (this) {
             request = m_pendingOpenRequest;
         }

         if (request == null) {
             destroy(new ChannelError("The server sent a invalid resolve"));
             return;
         }

         channel = request.getChannel();

         if (flag != Frame.OPEN_ALLOW) {
             error = new ChannelError("Unable to resolve path");
             channel.destroy(error);
             return;
         }

         if (HydnaDebug.HYDNADEBUG) {
             Charset charset;
             CharsetDecoder decoder;
             String content;
             int pos;

             pos = data.position();
             charset = Charset.forName("UTF-8");
             decoder = charset.newDecoder();
             content = null;

             try {
                 content = decoder.decode(data).toString();
             } catch (CharacterCodingException ex) {
             }

             DebugHelper.debugPrint("Connection",
                                    channelPtr,
                                    "received lookup for: '"
                                        + content + "'");
         }

         path = request.getPath();
         path.position(0);
         data.position(0);

         if (data.equals(path) == false) {
             error = new ChannelError("Bad path sent by server");
             channel.destroy(error);
             return;
         }

         request.setChannelPtr(channelPtr);
         channel.resolveSuccess();
    }

    /**
     *  Destroy the connection.
     *
     *  @error The cause of the destroy.
     */
    private void destroy(ChannelError error) {

        if (HydnaDebug.HYDNADEBUG) {
            String message = "clean shutdown";

            if (error != null) {
                message = error.getMessage();
            }

            DebugHelper.debugPrint("Connection",
                                   0,
                                   "Destroying connection because: "
                                       + message);
        }

        disposeConnection(this);

        synchronized (this) {
            m_destroying = true;
            m_handshaked = false;
        }

        if (m_pendingOpenRequest != null) {
            m_pendingOpenRequest.getChannel().destroy(error);
            m_pendingOpenRequest = null;
        }

        // It is safe to reset this members, its only
        // receiver that access them.
        m_listening = false;

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection",
                                   0,
                                   "Destroying openChannels of size "
                                       + m_openChannels.size());
        }

        for (Channel channel : m_openChannels.values()) {
            if (HydnaDebug.HYDNADEBUG) {
                DebugHelper.debugPrint("Connection",
                                       channel.getChannelPtr(),
                                       "Destroying channel");
            }
            channel.destroy(error);
        }				

        m_openChannels.clear();


        if (m_connected) {
            if (HydnaDebug.HYDNADEBUG) {
                DebugHelper.debugPrint("Connection", 0, "Closing connection");
            }

            try {
                m_socketChannel.close();
            } catch (IOException e) {
            } finally {
                m_socketChannel = null;
            }

            m_connected = false;
        }

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Connection", 0, "Destroying connection done");
        }

        synchronized (this) {
            m_destroying = false;
        }
    }


    /**
     *  Writes a frame to the connection.
     *
     *  @param frame The frame to be sent.
     *  @return True if the frame was sent.
     */
    boolean writeBytes(Frame frame) {

        synchronized (this) {
            if (m_handshaked == false ||
                m_destroying == true) {
                return false;
            }
        }

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
            // We do not destroy the connection at this point, even if we
            // we have a write error. The receiveHandler will take care of
            // it.
            return false;
        }

        return true;
    }
}