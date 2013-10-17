package com.hydna;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharacterCodingException;

public class ChannelEvent {

    private Channel m_target;

    private ByteBuffer m_data;
    private int m_ctype;
	
    public ChannelEvent(Channel target, int ctype, ByteBuffer data) {
        m_data = data;
        m_ctype = ctype;
    }


    /**
     *  Returns the Channel which this event belongs to.
     *
     *  @return Channel the underlying target channel
     */
    public Channel getChannel() {
        return m_target;
    }


    /**
     *  Returns true if the content is flagged as Binary, else false
     *
     *  @return Boolean true if content is flagged as Binary
     */
    public boolean isBinaryContent() {
        return m_ctype == ContentType.BINARY;
    }

    /**
     *  Returns true if the content is flagged as UTF-8, else false.
     *
     *  @return Boolean true if content is flagged as UTF-8
     */
    public boolean isUtf8Content() {
        return m_ctype == ContentType.UTF8;
    }

    /**
     *  Returns the data associated with this ChannelData instance.
     *
     *  @return The content.
     */
    public ByteBuffer getData() {
        return m_data;
    }

    /**
     *  Returns the data associated with this ChannelData instance as
     * an UTF-8 String.
     *
     *  @return The content or null if not of type UTF-8.
     */
    public String getString() {
        Charset charset;
        CharsetDecoder decoder;
        String content;
        int pos;

        if (isUtf8Content() == false) {
            return null;
        }

        pos = m_data.position();
        charset = Charset.forName("UTF-8");
        decoder = charset.newDecoder();
        content = null;

        try {
            content = decoder.decode(m_data).toString();
        } catch (CharacterCodingException ex) {
        } finally {
            m_data.position(pos);
        }

        return content;
    }
}
