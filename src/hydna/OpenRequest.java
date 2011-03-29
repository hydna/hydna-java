package hydna;

/**
 *  This class is used internally by both the Stream and the ExtSocket class.
 *  A user of the library should not create an instance of this class.
 */
public class OpenRequest {
	private Stream m_stream;
	private int m_addr;
	private Packet m_packet;
	private boolean m_sent = false;
	
	public OpenRequest(Stream stream, int addr, Packet packet) {
		m_stream = stream;
		m_addr = addr;
		m_packet = packet;
	}
	
	public Stream getStream() {
		return m_stream;
	}
	
	public int getAddr() {
		return m_addr;
	}
	
	public Packet getPacket() {
		return m_packet;
	}
	
	public boolean isSent() {
		return m_sent;
	}
	
	public void setSent(boolean value) {
		m_sent = value;
	}
}
