package hydna;

import java.nio.ByteBuffer;

public class ChannelSignal {
	private int m_type;
	private ByteBuffer m_content;
	
	public ChannelSignal(int type, ByteBuffer content) {
		m_type = type;
		m_content = content;
	}
	
	/**
     *  Returns the type of the content.
     *
     *  @return The type of the content.
     */
	public int getType() {
		return m_type;
	}
	
	/**
     *  Returns the content associated with this ChannelSignal instance.
     *
     *  @return The content.
     */
	public ByteBuffer getContent() {
		return m_content;
	}
}
