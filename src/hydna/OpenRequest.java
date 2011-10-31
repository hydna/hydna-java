package hydna;

/**
 *  This class is used internally by both the Channel and the ExtSocket class.
 *  A user of the library should not create an instance of this class.
 */
public class OpenRequest {
	private Channel m_channel;
	private int m_ch;
	private Packet m_packet;
	private boolean m_sent = false;
	
	public OpenRequest(Channel channel, int ch, Packet packet) {
		m_channel = channel;
		m_ch = ch;
		m_packet = packet;
	}
	
	public Channel getChannel() {
		return m_channel;
	}
	
	public int getChannelId() {
		return m_ch;
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
