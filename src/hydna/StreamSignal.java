package hydna;

import java.nio.ByteBuffer;

public class StreamSignal {
	private int m_type;
	private ByteBuffer m_content;
	
	public StreamSignal(int type, ByteBuffer content) {
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
     *  Returns the content associated with this StreamSignal instance.
     *
     *  @return The content.
     */
	public ByteBuffer getContent() {
		return m_content;
	}
}
