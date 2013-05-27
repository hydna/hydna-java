package hydna;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharacterCodingException;

public class ChannelData {
	private int m_priority;
	private boolean m_binary;
	private ByteBuffer m_content;
	
	public ChannelData(int flag, ByteBuffer content) {
		m_priority = (flag >> 1);
		m_binary = (flag & 1) == 1 ? Boolean.FALSE : Boolean.TRUE;
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
     *  Returns true if the content is flagged as Binary, else false
     *
     *  @return Boolean true if content is flagged as Binary
     */
	public boolean isBinaryContent() {
		return m_binary;
	}

	/**
     *  Returns true if the content is flagged as UTF-8, else false.
     *
     *  @return Boolean true if content is flagged as UTF-8
     */
	public boolean isUtf8Content() {
		return !m_binary;
	}

	/**
     *  Returns the data associated with this ChannelData instance.
     *
     *  @return The content.
     */
	public ByteBuffer getContent() {
		return m_content;
	}

	/**
     *  Returns the data associated with this ChannelData instance as an UTF-8 String.
     *
     *  @return The content or null if not of type UTF-8.
     */
	public String getString() {
		int pos = m_content.position();
		Charset charset = Charset.forName("UTF-8");
    	CharsetDecoder decoder = charset.newDecoder();
    	String content = null;

    	if (isUtf8Content() == false) {
    		return null;
    	}

    	try {
        	content = decoder.decode(m_content).toString();
    	} catch (CharacterCodingException ex) {
    	} finally {
        	m_content.position(pos);
    	}
		return content;
	}
}
