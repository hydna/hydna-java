package com.hydna;

import java.io.UnsupportedEncodingException;

import java.nio.ByteBuffer;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;


/**
 *  This class is used as an interface to the library.
 *  A user of the library should use an instance of this class
 *  to communicate with a server.
 */
public class Channel {

    private int m_channelPtr = 0;
    private String m_path;

    private Connection m_connection = null;
    private boolean m_connected = false;
    private boolean m_closing = false;

    private int m_mode;

    private Queue<ChannelSignal> m_signalQueue;
    private Queue<ChannelEvent> m_eventQueue = null;

    private ChannelEvent m_openEvent = null;
    private ChannelEvent m_endEvent = null;
    private ChannelError m_error = null;

    private final Semaphore m_waitLock = new Semaphore(0, true);

    /**
     *  Initializes a new Channel instance
     */
    public Channel() {
        m_eventQueue = new ConcurrentLinkedQueue<ChannelEvent>();
    }

    /**
     *  Get the underlying Path for this Channel
     *
     *  @return The path of this Channel.
     */
    synchronized public String getPath() {
        return m_path;
    }
	
    /**
     *  Checks the connected state for this Channel instance.
     *
     *  @return The connected state.
     */
    synchronized public boolean isConnected() {
        return m_connected;
    }

    /**
     *  Checks the closing state for this Channel instance.
     *
     *  @return The closing state.
     */
    synchronized public boolean isClosing() {
        return m_closing;
    }

    /**
     *  Checks if the channel is readable.
     *
     *  @return True if channel is readable.
     */
    synchronized public boolean isReadable() {
        return m_connected &&
               m_closing == false &&
               ((m_mode & ChannelMode.READ) == ChannelMode.READ);
    }

    /**
     *  Checks if the channel is writable.
     *
     *  @return True if channel is writable.
     */
    synchronized public boolean isWritable() {
        return m_connected &&
               m_closing == false &&
               ((m_mode & ChannelMode.WRITE) == ChannelMode.WRITE);
    }

    /**
     *  Checks if the channel can emit signals.
     *
     *  @return True if channel has signal support.
     */
    synchronized public boolean isEmitable() {
        return m_connected &&
               m_closing == false &&
               ((m_mode & ChannelMode.EMIT) == ChannelMode.EMIT);
    }

    /**
     *  Checks if the channel has an Error attached
     *
     *  @return True if channel has an Error attached.
     */
    synchronized public boolean hasError() {
        return !(m_error == null);
    }

    /**
     *  Checks if the channel has a pending end signal attached
     *
     *  @return True if channel has a pendning end signal attached.
     */
    synchronized public boolean hasEndSignal() {
        return !(m_endEvent == null);
    }



    /**
     *  Connects the channel to the specified channel. If the connection 
     *  fails, an exception is thrown.
     *
     *  @param urlExpr The URL to connect to,
     *  @param mode The mode in which to open the channel.
     *  @param token An optional token.
     */
    public ChannelEvent connect(String urlExpr, int mode)
        throws ChannelError, InterruptedException {
        Connection connection;
        Frame frame;
        OpenRequest request;
        ChannelEvent openEvent;
        ChannelError error;
        ByteBuffer token = null;
        ByteBuffer path = null;
  
        synchronized (this) {
            if (m_connection != null) {
                throw new ChannelError("Already connected");
            }

            if (m_closing) {
                throw new ChannelError("Channel is closing");
            }
        }

        if (mode < ChannelMode.LISTEN ||
            mode > ChannelMode.READWRITEEMIT) {
            throw new ChannelError("Invalid channel mode");
        }
  
        m_error = null;
        m_endEvent = null;
  
        m_mode = mode;
  
        URL url = URL.parse(urlExpr);
        String tokens = "";
        String chs = "";
        int ch;
        int pos;
    
        // Host can be on the form "http://auth@localhost:80/x00112233?token"
    
    
        if (!url.getProtocol().equals("http")) {
            if (url.getProtocol().equals("https")) {
                throw new Error("The protocol HTTPS is not supported");
            } else {
                throw new Error("Unknown protocol, " + url.getProtocol());
            }
        }
    
        if (!url.getError().equals("")) {
            throw new Error(url.getError());
        }

        m_path = url.getPath();

        if (m_path.length() == 0 || m_path.charAt(0) != '/') {
            m_path = "/" + m_path;
        }

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Channel",
                                   0,
                                   "Path set to '" + m_path + "'");
        }

        try {
            path = ByteBuffer.wrap(m_path.getBytes("US-ASCII"));
        } catch (UnsupportedEncodingException e) {
            throw new ChannelError("Unable to encode path");
        }

        tokens = url.getToken();

        connection = Connection.getConnection(url.getHost(), url.getPort());
        m_connection = connection;

        // Ref count
        connection.allocChannel();

        if (tokens != "") {
            try {
                token = ByteBuffer.wrap(tokens.getBytes("UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new ChannelError("Unable to encode token data");
            }
        }
 
        request = new OpenRequest(this, path, mode, token);

        connection.requestOpen(request);

        connection.writeBytes(request.getResolveFrame());

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Channel",
                                   0,
                                   "Acquire waitLock");
        }

        m_waitLock.acquire();

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Channel",
                                   0,
                                   "release waitLock");
        }

        if ((error = resetError()) != null) {
            throw error;
        }

        connection.writeBytes(request.getFrame());

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Channel",
                                   0,
                                   "Acquire waitLock after resolve");
        }

        m_waitLock.acquire();

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Channel",
                                   getChannelPtr(),
                                   "Release waitLock after resolve");
        }

        if ((error = resetError()) != null) {
            throw error;
        }

        openEvent = m_openEvent;

        m_openEvent = null;

        return openEvent;
    }

    /**
     *  Pop the next ChannelEvent in the event queue. The event is
     *  is either a ChannelData instance, a ChannelSignal instance 
     *  or a ChannelEndSignal instance.
     *
     *  The method is blocking until an Event has arrived, if queue
     *  is empty.
     *
     *  @return The ChannelEvent that was removed from the queue,
     *          or NULL if the queue was empty.
     */
    public ChannelEvent nextEvent()
        throws ChannelError, InterruptedException {
        ChannelEvent event;
        ChannelError error;

        if ((error = resetError()) != null) {
            throw error;
        }

        if ((event = resetEndEvent()) != null) {
            return event;
        }

        event = m_eventQueue.poll();

        if (event == null) {
            m_waitLock.acquire();
            return nextEvent();
        }

        return event;
    }

    /**
     *  Checks if the event queue is empty. This function also returns
     *  true if their is an error pending.
     *
     *  @return True is the queue is empty or if there is a channel error.
     */
    public boolean hasEvents() {

        if (hasError()) {
            return true;
        }

        if (hasEndSignal()) {
            return true;
        }

        return m_eventQueue.isEmpty() != true;
    }

    /**
     *  Sends a UTF8 data message to the channel with priority 0.
     *
     *  @param data The payload to write to the channel.
     */
    public boolean send(String message) throws ChannelError {
        ByteBuffer data;
        try {
            data = ByteBuffer.wrap(message.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new ChannelError("Unable to encode payload");
        }
        return send(ContentType.UTF8, 0, data);
    }

    /**
     *  Sends a UTF8 data message to the channel with specified priority.
     *
     *  @param data The payload to write to the channel.
     *  @param priority The priority of the payload.
     */
    public boolean send(String message, int priority) throws ChannelError {
        ByteBuffer data;
        try {
            data = ByteBuffer.wrap(message.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new ChannelError("Unable to encode payload");
        }
        return send(ContentType.BINARY, priority, data);
    }

    /**
     *  Sends a binary data message to the channel with priority 0.
     *
     *  @param data The payload to write to the channel.
     */
    public boolean send(ByteBuffer data) throws ChannelError {
        return send(ContentType.BINARY, 0, data);
    }

    /**
     *  Sends a binary data message with specified priority.
     *
     *  @param data The payload to write to the channel.
     *  @param priority The priority of the payload.
     */
    public boolean send(ByteBuffer data, int priority) throws ChannelError {
        return send(ContentType.BINARY, priority, data);
    }

    /**
     *  Sends UTF8 signal to the channel.
     *
     *  @param data The data to write to the channel.
     *  @param type The type of the signal.
     */
    public boolean emit(String message) throws ChannelError {
        ByteBuffer data;
        try {
            data = ByteBuffer.wrap(message.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new ChannelError("Unable to encode payload");
        }
        return emit(ContentType.UTF8, data);
    }

    /**
     *  Sends a binary signal to the channel.
     *
     *  @param data The data to write to the channel.
     *  @param type The type of the signal.
     */
    public boolean emit(ByteBuffer data) throws ChannelError {
        return emit(ContentType.BINARY, data);
    }

    /**
     *  Closes the Channel instance without any message.
     */
    public void close() throws ChannelError, InterruptedException {
        close(ContentType.UTF8, null);
    }

    /**
     *  Closes the Channel instance with a UTF8 message.
     */
    public void close(String message)
        throws ChannelError, InterruptedException {
        ByteBuffer data;
        try {
            data = ByteBuffer.wrap(message.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new ChannelError("Unable to encode payload");
        }
        close(ContentType.UTF8, data);
    }

    /**
     *  Closes the Channel instance with a binary message.
     */
    public void close(ByteBuffer data)
        throws ChannelError, InterruptedException {
        close(ContentType.BINARY, data);
    }

    /**
     *  Add an Event to the event queue.
     *
     *  @param event The event to add to queue.
     */
    void addEvent(ChannelEvent event) {
        boolean hasWaitingThread;

        m_eventQueue.add(event);

        synchronized (this) {
            hasWaitingThread = m_waitLock.hasQueuedThreads();
        }

        if (hasWaitingThread) {
            m_waitLock.release();
        }
    }

    /**
     *  Returns the channel that this instance listen to.
     *
     *  @return The channel.
     */
    synchronized int getChannelPtr() {
        return m_channelPtr;
    }

    /**
     *  Resets the error
     *
     *  @return The Error attached to the channel instance.
     */
    synchronized ChannelError resetError() {
        ChannelError error = m_error;
        m_error = null;
        return error;
    }

    /**
     *  Resets current end event
     *
     *  @return The end signal attached to the channel instance.
     */
    synchronized ChannelEvent resetEndEvent() {
        ChannelEvent event = m_endEvent;
        m_endEvent = null;
        return event;
    }

    /**
     *  Get the underlying connection to this channel instance
     *
     *  @return The underlying connection
     */
    synchronized Connection getUnderlyingConnection() {
        return m_connection;
    }

    /**
     *  Internal callback for open success.
     *  Used by the Connection class.
     *
     *  @param ch The channel pointer.
     *  @param ctype The ContentType
     *  @param payload Optional payload
     */
    synchronized void openSuccess(int channelPtr,
                                  int ctype,
                                  ByteBuffer data) {
        m_channelPtr = channelPtr;
        m_connected = true;
        m_openEvent = new ChannelData(this, ctype, 0, data);
        m_waitLock.release();
    }

    void resolveSuccess() {
        m_waitLock.release();
    }

    synchronized void destroy(ChannelError error) {
        destroy(error, null);
    }

    synchronized void destroy(ChannelSignal event) {
        destroy(null, event);
    }

    /**
     *  Internally destroy channel.
     *
     *  @param error The cause of the destroy.
     */
    synchronized void destroy(ChannelError error,
                              ChannelSignal event) {
        Connection connection = m_connection;
        boolean connected = m_connected;
        int channelPtr = m_channelPtr;
        boolean closing = m_closing;
        Frame frame;

        m_channelPtr = 0;
        m_connected = false;
        m_connection = null;

        if (connection != null) {

            // Tell server that we received the end signal
            if (event != null && closing == false) {
                frame = Frame.create(channelPtr,
                                     ContentType.UTF8,
                                     Frame.SIGNAL,
                                     Frame.SIG_END);
                connection.writeBytes(frame);
            }

            connection.deallocChannel(connected ? channelPtr : 0);
        }

        m_error = error;
        m_endEvent = event;

        m_eventQueue.clear();

        m_waitLock.release();
    }

    /**
     *  Sends a binary data message with specified priority and ContentType.
     *
     *  @param ctype The ContentType of the payload
     *  @param priority The priority of the payload.
     *  @param data The payload to write to the channel.
     */
    private boolean send(int ctype, int priority, ByteBuffer data)
        throws ChannelError {
        Connection connection;
        Frame frame;

        if (data == null || data.capacity() == 0) {
            throw new ChannelError("Payload data cannot be zero-length");
        }

        if (priority < 0 || priority > 7) {
            throw new ChannelError("Priority must be between 0 - 7");
        }

        if (isConnected() == false ||
            (connection = getUnderlyingConnection()) == null) {
            throw new ChannelError("Not connected");
        }

        if (isWritable() == false) {
            throw new ChannelError("You do not have permission to send data");
        }

        frame = Frame.create(m_channelPtr,
                             ctype,
                             Frame.DATA,
                             priority,
                             data);

        return connection.writeBytes(frame);
    }

    /**
     *  Sends data signal to the channel.
     *
     *  @param data The data to write to the channel.
     *  @param type The type of the signal.
     */
    private boolean emit(int ctype, ByteBuffer data)
        throws ChannelError {
        Connection connection;
        Frame frame;

        if (data == null || data.capacity() == 0) {
            throw new ChannelError("Payload data cannot be zero-length");
        }

        if (isConnected() == false ||
            (connection = getUnderlyingConnection()) == null) {
            throw new ChannelError("Not connected");
        }

        if (isEmitable() == false) {
            throw new ChannelError("You do not have permission to send signals");
        }

        frame = Frame.create(m_channelPtr,
                             ctype,
                             Frame.SIGNAL,
                             Frame.SIG_EMIT,
                             data);

        return connection.writeBytes(frame);
    }

    /**
     *  Closes the Channel instance.
     */
    private void close(int ctype, ByteBuffer data)
        throws ChannelError, InterruptedException {
        Connection connection;
        Frame frame;
        ChannelError error;

        if (isConnected() == false ||
            (connection = getUnderlyingConnection()) == null) {
            throw new ChannelError("The channel is not open");
        }

        m_closing = true;

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Channel",
                                   getChannelPtr(),
                                   "Sending close signal");
        }

        frame = Frame.create(getChannelPtr(),
                             ctype,
                             Frame.SIGNAL,
                             Frame.SIG_END,
                             data);

        try {
            connection.writeBytes(frame);
        } catch (Exception e) {
            m_connected = false;
            m_closing = false;
        }

        if (HydnaDebug.HYDNADEBUG) {
            DebugHelper.debugPrint("Channel",
                                   getChannelPtr(),
                                   "Acquire waitLock");
        }

        m_waitLock.acquire();
        m_closing = false;

        if ((error = resetError()) != null) {
            throw error;
        }
    }
}
