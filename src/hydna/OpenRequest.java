package hydna;

/**
 *  This class is used internally by both the Channel and the Connection class.
 *  A user of the library should not create an instance of this class.
 */
public class OpenRequest {
	private Channel m_channel;
	private int m_ch;
	private Frame m_frame;
	private boolean m_sent = false;
	
	public OpenRequest(Channel channel, int ch, Frame frame) {
		m_channel = channel;
		m_ch = ch;
		m_frame = frame;
	}
	
	public Channel getChannel() {
		return m_channel;
	}
	
	public int getChannelId() {
		return m_ch;
	}
	
	public Frame getFrame() {
		return m_frame;
	}
	
	public boolean isSent() {
		return m_sent;
	}
	
	public void setSent(boolean value) {
		m_sent = value;
	}
}
