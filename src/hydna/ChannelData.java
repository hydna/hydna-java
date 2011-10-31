package hydna;

import java.nio.ByteBuffer;

public class ChannelData {
	private int m_priority;
	private ByteBuffer m_content;
	
	public ChannelData(int priority, ByteBuffer content) {
		m_priority = priority;
		m_content = content;
	}
	
	/**
     *  Returns the priority of the content.
     *
     *  @return The priority of the content.
     */
	public int getPriority() {
		return m_priority;
	}
	
	/**
     *  Returns the data associated with this ChannelData instance.
     *
     *  @return The content.
     */
	public ByteBuffer getContent() {
		return m_content;
	}
}
