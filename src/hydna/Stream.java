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
public class Stream {
	private String m_host ="";
	private short m_port = 7010;
	private int m_addr = 1;
	
	private ExtSocket m_socket = null;
	private boolean m_connected = false;
	private boolean m_pendingClose = false;
	
	private boolean m_readable = false;
	private boolean m_writable = false;
	private boolean m_emitable = false;
	
	private StreamError m_error = new StreamError("", 0x0);
	
	private int m_mode;
	private OpenRequest m_openRequest = null;
	
	private Queue<StreamData> m_dataQueue = new LinkedList<StreamData>();
	private Queue<StreamSignal> m_signalQueue = new LinkedList<StreamSignal>();
	
	private Lock m_dataMutex = new ReentrantLock();
	private Lock m_signalMutex = new ReentrantLock();
	private Lock m_connectMutex = new ReentrantLock();
	
	/**
     *  Initializes a new Stream instance
     */
	public Stream() {}
	
	/**
     *  Checks the connected state for this Stream instance.
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
     *  Checks if the stream is readable.
     *
     *  @return True if stream is readable.
     */
	public boolean isReadable() {
		m_connectMutex.lock();
        boolean result = m_connected && m_readable;
        m_connectMutex.unlock();
        return result;
	}
	
	/**
     *  Checks if the stream is writable.
     *
     *  @return True if stream is writable.
     */
	public boolean isWritable() {
		m_connectMutex.lock();
        boolean result = m_connected && m_writable;
        m_connectMutex.unlock();
        return result;
	}
	
	/**
     *  Checks if the stream can emit signals.
     *
     *  @return True if stream has signal support.
     */
	public boolean hasSignalSupport() {
		m_connectMutex.lock();
        boolean result = m_connected && m_emitable && !m_pendingClose;
        m_connectMutex.unlock();
        return result;
	}
	
	/**
     *  Returns the addr that this instance listen to.
     *
     *  @return addr The address.
     */
	public int getAddr() {
		m_connectMutex.lock();
        int result = m_addr;
        m_connectMutex.unlock();
        return result;
	}
	
	/**
     *  Resets the error.
     *  
     *  Connects the stream to the specified addr. If the connection fails 
     *  immediately, an exception is thrown.
     *
     *  @param expr The address to connect to,
     *  @param mode The mode in which to open the stream.
     */
	public void connect(String expr, int mode) throws StreamError {
		connect(expr, mode, null);
	}
	
	/**
     *  Resets the error.
     *  
     *  Connects the stream to the specified addr. If the connection fails 
     *  immediately, an exception is thrown.
     *
     *  @param expr The address to connect to,
     *  @param mode The mode in which to open the stream.
     *  @param token An optional token.
     */
	public void connect(String expr, int mode, ByteBuffer token) throws StreamError {
		Packet packet;
        OpenRequest request;
      
        m_connectMutex.lock();
        if (m_socket != null) {
            m_connectMutex.unlock();
            throw new StreamError("Already connected");
        }
        m_connectMutex.unlock();

        if (mode == 0x04 ||
                mode < StreamMode.READ || 
                mode > StreamMode.READWRITE_EMIT) {
            throw new StreamError("Invalid stream mode");
        }
      
        m_mode = mode;
      
        m_readable = ((m_mode & StreamMode.READ) == StreamMode.READ);
        m_writable = ((m_mode & StreamMode.WRITE) == StreamMode.WRITE);
        m_emitable = ((m_mode & StreamMode.EMIT) == StreamMode.EMIT);

        String host = expr;
        short port = 7010;
        int addr = 1;
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
            	addr = Integer.parseInt(host.substring(pos + 2), 16);
            } catch (NumberFormatException e) {
            	throw new StreamError("Could not read the address \"" + host.substring(pos + 2) + "\"");
            }
            
            host = host.substring(0, pos);
        } else {
            pos = host.lastIndexOf("/");
            if (pos != -1) {
                try {
                	addr = Integer.parseInt(host.substring(pos + 1), 10);
                } catch (NumberFormatException e) {
                   throw new StreamError("Could not read the address \"" + host.substring(pos + 1) + "\""); 
                }
                
                host = host.substring(0, pos);
            }
        }

        pos = host.lastIndexOf(":");
        if (pos != -1) {
        	try {
        	port = Short.parseShort(host.substring(pos + 1), 10);
        	} catch (NumberFormatException e) {
               throw new StreamError("Could not read the port \"" + host.substring(pos + 1) + "\""); 
            }
        	
        	host = host.substring(0, pos);
        }
        
        m_host = host;
        m_port = port;
        m_addr = addr;

        m_socket = ExtSocket.getSocket(m_host, m_port);
      
        // Ref count
        m_socket.allocStream();

        if (token != null || tokens == "") {
            packet = new Packet(m_addr, Packet.OPEN, mode, token);
        } else {
            packet = new Packet(m_addr, Packet.OPEN, mode, ByteBuffer.wrap(tokens.getBytes()));
        }
      
        request = new OpenRequest(this, m_addr, packet);

        m_error = new StreamError("", 0x0);
      
        if (!m_socket.requestOpen(request)) {
            checkForStreamError();
            throw new StreamError("Stream already open");
        }

        m_openRequest = request;
	}
	
	/**
     *  Sends data to the stream.
     *
     *  @param data The data to write to the stream.
     *  @param priority The priority of the data.
     */
	public void writeBytes(ByteBuffer data, int priority) throws StreamError {
		boolean result;

        m_connectMutex.lock();
        if (!m_connected || m_socket == null) {
            m_connectMutex.unlock();
            checkForStreamError();
            throw new StreamError("Stream is not connected");
        }
        m_connectMutex.unlock();

        if ((m_mode & StreamMode.WRITE) != StreamMode.WRITE) {
            throw new StreamError("Stream is not writable");
        }
      
        if (priority > 3 || priority == 0) {
            throw new StreamError("Priority must be between 1 - 3");
        }

        Packet packet = new Packet(m_addr, Packet.DATA, priority,
                                data);
      
        m_connectMutex.lock();
        ExtSocket socket = m_socket;
        m_connectMutex.unlock();
        result = socket.writeBytes(packet);

        if (!result)
            checkForStreamError();
	}
	
	/**
     *  Sends data to the stream.
     *
     *  @param data The data to write to the stream.
     */
	public void writeBytes(ByteBuffer data) throws StreamError {
		writeBytes(data, 1);
	}
	
	/**
     *  Sends string data to the stream.
     *
     *  @param value The string to be sent.
     */
	public void writeString(String value) throws StreamError {
		writeBytes(ByteBuffer.wrap(value.getBytes()));
	}
	
	/**
     *  Sends data signal to the stream.
     *
     *  @param data The data to write to the stream..
     *  @param type The type of the signal.
     */
	public void emitBytes(ByteBuffer data, int type) throws StreamError {
		boolean result;

        m_connectMutex.lock();
        if (!m_connected || m_socket == null) {
            m_connectMutex.unlock();
            checkForStreamError();
            throw new StreamError("Stream is not connected.");
        }
        m_connectMutex.unlock();

        if ((m_mode & StreamMode.EMIT) != StreamMode.EMIT) {
            throw new StreamError("You do not have permission to send signals");
        }

        Packet packet = new Packet(m_addr, Packet.SIGNAL, type,
                            data);

        m_connectMutex.lock();
        ExtSocket socket = m_socket;
        m_connectMutex.unlock();
        result = socket.writeBytes(packet);

        if (!result)
            checkForStreamError();
	}
	
	/**
     *  Sends data signal to the stream.
     *
     *  @param data The data to write to the stream..
     */
	public void emitBytes(ByteBuffer data) throws StreamError {
		emitBytes(data, 0);
	}
	
	/**
     *  Sends a string signal to the stream.
     *
     *  @param value The string to be sent.
     *  @param type The type of the signal.
     */
	public void emitString(String value, int type) throws StreamError {
		emitBytes(ByteBuffer.wrap(value.getBytes()), type);
	}
	
	/**
     *  Sends a string signal to the stream.
     *
     *  @param value The string to be sent.
     */
	public void emitString(String value) throws StreamError {
		emitString(value, 0);
	}
	
	/**
     *  Closes the Stream instance.
     */
	public void close() {
		m_connectMutex.lock();
        if (m_socket == null || m_pendingClose) {
            m_connectMutex.unlock();
            return;
        }
      
        if (m_openRequest != null) {
            if (m_socket.cancelOpen(m_openRequest)) {
                m_openRequest = null;
                m_connectMutex.unlock();
                
                StreamError error = new StreamError("", 0x0);
                destroy(error);
            } else {
                m_pendingClose = true;
                m_connectMutex.unlock();
            }
        } else {
            m_connectMutex.unlock();
            internalClose();
        }
	}
	
	/**
     *  Checks if some error has occured in the stream
     *  and throws an exception if that is the case.
     */
	public void checkForStreamError() throws StreamError {
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
	protected void addData(StreamData data) {
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
	public StreamData popData() {
		m_dataMutex.lock();
        StreamData data = m_dataQueue.poll();
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
	protected void addSignal(StreamSignal signal) {
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
	public StreamSignal popSignal() {
		m_signalMutex.lock();
		StreamSignal signal = m_signalQueue.poll();
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
     *  @param respaddr The response address.
     */
	protected void openSuccess(int respaddr) {
		m_connectMutex.lock();
        m_addr = respaddr;
        m_connected = true;
        m_openRequest = null;
      
        if (m_pendingClose) {
            m_connectMutex.unlock();
            internalClose();
        } else {
            m_connectMutex.unlock();
        }
	}
	
	/**
     *  Internally destroy socket.
     *
     *  @param error The cause of the destroy.
     */
	protected void destroy(StreamError error) {
		m_connectMutex.lock();

        m_pendingClose = false;
        m_writable = false;
        m_readable = false;
      
        if (m_socket != null) {
            m_socket.deallocStream(m_connected ? m_addr : 0);
        }

        m_connected = false;
        m_addr = 1;
        m_openRequest = null;
        m_socket = null;
        m_error = error;

        m_connectMutex.unlock();
	}
	
	/**
     *  Internally close the stream.
     */
	private void internalClose() {
		m_connectMutex.lock();
        if (m_socket != null && m_connected) {
            m_connectMutex.unlock();
            
			if (HydnaDebug.HYDNADEBUG) {
				System.out.println("Stream: Sending close signal");
			}
			
            Packet packet = new Packet(m_addr, Packet.SIGNAL, Packet.SIG_END, null);

            m_connectMutex.lock();
            ExtSocket socket = m_socket;
            m_connectMutex.unlock();
            socket.writeBytes(packet);
        } else {
            m_connectMutex.unlock();
        }

        StreamError error = new StreamError("", 0x0);
        destroy(error);
	}
}
