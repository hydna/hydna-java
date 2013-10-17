package com.hydna;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharacterCodingException;

public class ChannelData extends ChannelEvent  {

    private int m_priority;
	
    public ChannelData(Channel target, int ctype, int flag, ByteBuffer data) {
        super(target, ctype, data);
        m_priority = flag;
    }
	
    /**
     *  Returns the priority of the content.
     *
     *  @return The priority of the content.
     */
    public int getPriority() {
        return m_priority;
    }
}
