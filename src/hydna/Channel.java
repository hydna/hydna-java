package hydna;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *  This class is used as an interface to the library.
 *  A user of the library should use an instance of this class
 *  to communicate with a server.
 */
public class Channel {
	private String m_host ="";
	private short m_port = 7010;
	private int m_ch = 0;
	
	private ExtSocket m_socket = null;
	private boolean m_connected = false;
	private boolean m_closing = false;
	private Packet m_pendingClose;
	
	private boolean m_readable = false;
	private boolean m_writable = false;
	private boolean m_emitable = false;
	
	private ChannelError m_error = new ChannelError("", 0x0);
	
	private int m_mode;
	private OpenRequest m_openRequest = null;
	
	private Queue<ChannelData> m_dataQueue = new LinkedList<ChannelData>();
	private Queue<ChannelSignal> m_signalQueue = new LinkedList<ChannelSignal>();
	
	private Lock m_dataMutex = new ReentrantLock();
	private Lock m_signalMutex = new ReentrantLock();
	private Lock m_connectMutex = new ReentrantLock();
	
	/**
     *  Initializes a new Channel instance
     */
	public Channel() {}
	
	/**
     *  Checks the connected state for this Channel instance.
     *
     *  @return The connected state.
     */
	public boolean isConnected() {
		m_connectMutex.lock();
		boolean result = m_connected;
		m_connectMutex.unlock();
		return result;
	}
	
	/**
     *  Checks the closing state for this Channel instance.
     *
     *  @return The closing state.
     */
	public boolean isClosing() {
		m_connectMutex.lock();
		boolean result = m_closing;
		m_connectMutex.unlock();
		return result;
	}
	
	/**
     *  Checks if the channel is readable.
     *
     *  @return True if channel is readable.
     */
	public boolean isReadable() {
		m_connectMutex.lock();
        boolean result = m_connected && m_readable;
        m_connectMutex.unlock();
        return result;
	}
	
	/**
     *  Checks if the channel is writable.
     *
     *  @return True if channel is writable.
     */
	public boolean isWritable() {
		m_connectMutex.lock();
        boolean result = m_connected && m_writable;
        m_connectMutex.unlock();
        return result;
	}
	
	/**
     *  Checks if the channel can emit signals.
     *
     *  @return True if channel has signal support.
     */
	public boolean hasSignalSupport() {
		m_connectMutex.lock();
        boolean result = m_connected && m_emitable;
        m_connectMutex.unlock();
        return result;
	}
	
	/**
     *  Returns the channel that this instance listen to.
     *
     *  @return The channel.
     */
	public int getChannel() {
		m_connectMutex.lock();
        int result = m_ch;
        m_connectMutex.unlock();
        return result;
	}
	
	/**
     *  Resets the error.
     *  
     *  Connects the channel to the specified channel. If the connection fails 
     *  immediately, an exception is thrown.
     *
     *  @param expr The channel to connect to,
     *  @param mode The mode in which to open the channel.
     */
	public void connect(String expr, int mode) throws ChannelError {
		connect(expr, mode, null);
	}
	
	/**
     *  Resets the error.
     *  
     *  Connects the channel to the specified channel. If the connection fails 
     *  immediately, an exception is thrown.
     *
     *  @param expr The channel to connect to,
     *  @param mode The mode in which to open the channel.
     *  @param token An optional token.
     */
	public void connect(String expr, int mode, ByteBuffer token) throws ChannelError {
		Packet packet;
        OpenRequest request;
      
        m_connectMutex.lock();
        if (m_socket != null) {
            m_connectMutex.unlock();
            throw new ChannelError("Already connected");
        }
        m_connectMutex.unlock();

        if (mode == 0x04 ||
                mode < ChannelMode.READ || 
                mode > ChannelMode.READWRITEEMIT) {
            throw new ChannelError("Invalid channel mode");
        }
      
        m_mode = mode;
      
        m_readable = ((m_mode & ChannelMode.READ) == ChannelMode.READ);
        m_writable = ((m_mode & ChannelMode.WRITE) == ChannelMode.WRITE);
        m_emitable = ((m_mode & ChannelMode.EMIT) == ChannelMode.EMIT);

        String host = expr;
        short port = 7010;
        int ch = 1;
        String tokens = "";
        int pos;
        
        pos = host.lastIndexOf("?");
        if (pos != -1) {
            tokens = host.substring(pos + 1);
            host = host.substring(0, pos);
        }

        pos = host.lastIndexOf("/x");
        if (pos != -1) {
            try {
            	ch = Integer.parseInt(host.substring(pos + 2), 16);
            } catch (NumberFormatException e) {
            	throw new ChannelError("Could not read the address \"" + host.substring(pos + 2) + "\"");
            }
            
            host = host.substring(0, pos);
        } else {
            pos = host.lastIndexOf("/");
            if (pos != -1) {
                try {
                	ch = Integer.parseInt(host.substring(pos + 1), 10);
                } catch (NumberFormatException e) {
                   throw new ChannelError("Could not read the address \"" + host.substring(pos + 1) + "\""); 
                }
                
                host = host.substring(0, pos);
            }
        }

        pos = host.lastIndexOf(":");
        if (pos != -1) {
        	try {
        	port = Short.parseShort(host.substring(pos + 1), 10);
        	} catch (NumberFormatException e) {
               throw new ChannelError("Could not read the port \"" + host.substring(pos + 1) + "\""); 
            }
        	
        	host = host.substring(0, pos);
        }
        
        m_host = host;
        m_port = port;
        m_ch = ch;

        m_socket = ExtSocket.getSocket(m_host, m_port);
      
        // Ref count
        m_socket.allocChannel();

        if (token != null || tokens == "") {
            packet = new Packet(m_ch, Packet.OPEN, mode, token);
        } else {
            packet = new Packet(m_ch, Packet.OPEN, mode, ByteBuffer.wrap(tokens.getBytes()));
        }
      
        request = new OpenRequest(this, m_ch, packet);

        m_error = new ChannelError("", 0x0);
      
        if (!m_socket.requestOpen(request)) {
            checkForChannelError();
            throw new ChannelError("Channel already open");
        }

        m_openRequest = request;
	}
	
	/**
     *  Sends data to the channel.
     *
     *  @param data The data to write to the channel.
     *  @param priority The priority of the data.
     */
	public void writeBytes(ByteBuffer data, int priority) throws ChannelError {
		boolean result;

        m_connectMutex.lock();
        if (!m_connected || m_socket == null) {
            m_connectMutex.unlock();
            checkForChannelError();
            throw new ChannelError("Channel is not connected");
        }
        m_connectMutex.unlock();

        if (!m_writable) {
            throw new ChannelError("Channel is not writable");
        }
      
        if (priority > 3 || priority == 0) {
            throw new ChannelError("Priority must be between 1 - 3");
        }

        Packet packet = new Packet(m_ch, Packet.DATA, priority,
                                data);
      
        m_connectMutex.lock();
        ExtSocket socket = m_socket;
        m_connectMutex.unlock();
        result = socket.writeBytes(packet);

        if (!result)
            checkForChannelError();
	}
	
	/**
     *  Sends data to the channel.
     *
     *  @param data The data to write to the channel.
     */
	public void writeBytes(ByteBuffer data) throws ChannelError {
		writeBytes(data, 1);
	}
	
	/**
     *  Sends string data to the channel.
     *
     *  @param value The string to be sent.
     */
	public void writeString(String value) throws ChannelError {
		writeBytes(ByteBuffer.wrap(value.getBytes()));
	}
	
	/**
     *  Sends data signal to the channel.
     *
     *  @param data The data to write to the channel.
     *  @param type The type of the signal.
     */
	public void emitBytes(ByteBuffer data, int type) throws ChannelError {
		boolean result;

        m_connectMutex.lock();
        if (!m_connected || m_socket == null) {
            m_connectMutex.unlock();
            checkForChannelError();
            throw new ChannelError("Channel is not connected.");
        }
        m_connectMutex.unlock();

        if (!m_emitable) {
            throw new ChannelError("You do not have permission to send signals");
        }

        Packet packet = new Packet(m_ch, Packet.SIGNAL, type,
                            data);

        m_connectMutex.lock();
        ExtSocket socket = m_socket;
        m_connectMutex.unlock();
        result = socket.writeBytes(packet);

        if (!result)
            checkForChannelError();
	}
	
	/**
     *  Sends data signal to the channel.
     *
     *  @param data The data to write to the channel.
     */
	public void emitBytes(ByteBuffer data) throws ChannelError {
		emitBytes(data, 0);
	}
	
	/**
     *  Sends a string signal to the channel.
     *
     *  @param value The string to be sent.
     *  @param type The type of the signal.
     */
	public void emitString(String value, int type) throws ChannelError {
		emitBytes(ByteBuffer.wrap(value.getBytes()), type);
	}
	
	/**
     *  Sends a string signal to the channel.
     *
     *  @param value The string to be sent.
     */
	public void emitString(String value) throws ChannelError {
		emitString(value, 0);
	}
	
	/**
     *  Closes the Channel instance.
     */
	public void close() {
		m_connectMutex.lock();
        if (m_socket == null || m_closing) {
            m_connectMutex.unlock();
            return;
        }
        
        m_closing = true;
        m_readable = false;
        m_writable = false;
        m_emitable = false;
      
        if (m_openRequest != null && m_socket.cancelOpen(m_openRequest)) {
        	// Open request hasn't been posted yet, which means that it's
            // safe to destroy channel immediately.
        	
        	m_openRequest = null;
        	m_connectMutex.unlock();
        	
        	ChannelError error = new ChannelError("", 0x0);
        	destroy(error);
        	return;
        }
        
        Packet packet = new Packet(m_ch, Packet.SIGNAL, Packet.SIG_END);
        
        if (m_openRequest != null) {
        	// Open request is not responded to yet. Wait to send ENDSIG until	
            // we get an OPENRESP.
        	
        	m_pendingClose = packet;
        	m_connectMutex.unlock();
        } else {
        	m_connectMutex.unlock();
        	
        	if (HydnaDebug.HYDNADEBUG) {
        		DebugHelper.debugPrint("Channel", m_ch, "Sending close signal");
			}
        	
        	m_connectMutex.lock();
        	ExtSocket socket = m_socket;
        	m_connectMutex.unlock();
        	socket.writeBytes(packet);
        	
        }
	}
	
	/**
     *  Checks if some error has occurred in the channel
     *  and throws an exception if that is the case.
     */
	public void checkForChannelError() throws ChannelError {
		m_connectMutex.lock();
        if (m_error.getCode() != 0x0) {
            m_connectMutex.unlock();
            throw m_error;
        } else {
            m_connectMutex.unlock();
        }
	}
	
	/**
     *  Add data to the data queue.
     *
     *  @param data The data to add to queue.
     */
	protected void addData(ChannelData data) {
		m_dataMutex.lock();
		m_dataQueue.add(data);
		m_dataMutex.unlock();
	}
	
	/**
     *  Pop the next data in the data queue.
     *
     *  @return The data that was removed from the queue,
     *          or NULL if the queue was empty.
     */
	public ChannelData popData() {
		m_dataMutex.lock();
        ChannelData data = m_dataQueue.poll();
        m_dataMutex.unlock();
        
        return data;
	}
	
	/**
     *  Checks if the signal queue is empty.
     *
     *  @return True if the queue is empty.
     */
	public boolean isDataEmpty() {
		m_dataMutex.lock();
        boolean result = m_dataQueue.isEmpty();
        m_dataMutex.unlock();
        
        return result;
	}
	
	/**
     *  Add signals to the signal queue.
     *
     *  @param signal The signal to add to the queue.
     */
	protected void addSignal(ChannelSignal signal) {
		m_signalMutex.lock();
		m_signalQueue.add(signal);
		m_signalMutex.unlock();
	}
	
	/**
     *  Pop the next signal in the signal queue.
     *
     *  @return The signal that was removed from the queue,
     *          or NULL if the queue was empty.
     */
	public ChannelSignal popSignal() {
		m_signalMutex.lock();
		ChannelSignal signal = m_signalQueue.poll();
		m_signalMutex.unlock();
		
		return signal;
	}
	
	/**
     *  Checks if the signal queue is empty.
     *
     *  @return True is the queue is empty.
     */
	public boolean isSignalEmpty() {
		m_signalMutex.lock();
		boolean result = m_signalQueue.isEmpty();
		m_signalMutex.unlock();
		
		return result;
	}
	
	/**
     *  Internal callback for open success.
     *  Used by the ExtSocket class.
     *
     *  @param respch The response channel.
     */
	protected void openSuccess(int respch) {
		m_connectMutex.lock();
		int origch = m_ch;
		Packet packet;
		
		m_openRequest = null;
        m_ch = respch;
        m_connected = true;
      
        if (m_pendingClose != null) {
        	packet = m_pendingClose;
        	m_pendingClose = null;
            m_connectMutex.unlock();
            
            if (origch != respch) {
            	// channel is changed. We need to change the channel of the
                //packet before sending to server.
            	
            	packet.setChannel(respch);
			}
            
            if (HydnaDebug.HYDNADEBUG) {
            	DebugHelper.debugPrint("Channel", m_ch, "Sending close signal");
			}
            
			m_connectMutex.lock();
			ExtSocket socket = m_socket;
			m_connectMutex.unlock();
			socket.writeBytes(packet);
        } else {
            m_connectMutex.unlock();
        }
	}
	
	/**
     *  Internally destroy socket.
     *
     *  @param error The cause of the destroy.
     */
	protected void destroy(ChannelError error) {
		m_connectMutex.lock();
		ExtSocket socket = m_socket;
		boolean connected = m_connected;
		int ch = m_ch;

		m_ch = 0;
		m_connected = false;
        m_writable = false;
        m_readable = false;
        m_pendingClose = null;
        m_closing = false;
        m_openRequest = null;
        m_socket = null;
      
        if (socket != null) {
            socket.deallocChannel(connected ? ch : 0);
        }
        
        m_error = error;

        m_connectMutex.unlock();
	}
}
