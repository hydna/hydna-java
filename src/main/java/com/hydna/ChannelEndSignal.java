package com.hydna;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharacterCodingException;

public class ChannelEndSignal extends ChannelSignal  {

    public ChannelEndSignal(Channel target,
                            int ctype,
                            ByteBuffer data) {
        super(target, ctype, data);
    }
}
