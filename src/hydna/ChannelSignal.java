package com.hydna;

import java.nio.ByteBuffer;

public class ChannelSignal extends ChannelEvent  {

    public ChannelSignal(Channel target,
                         int ctype,
                         ByteBuffer data) {
        super(target, ctype, data);
    }
}
