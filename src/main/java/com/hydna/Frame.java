package com.hydna;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class Frame {
    static final short HEADER_SIZE = 0x05;

    // Opcodes
    static final int KEEPALIVE = 0x00;
    static final int OPEN = 0x01;
    static final int DATA = 0x02;
    static final int SIGNAL = 0x03;
    static final int RESOLVE = 0x04;

    // Open Flags
    static final int OPEN_ALLOW = 0x0;
    static final int OPEN_REDIRECT = 0x1;
    static final int OPEN_DENY = 0x7;

    // Signal Flags
    static final int SIG_EMIT = 0x0;
    static final int SIG_END = 0x1;
    static final int SIG_ERROR = 0x7;

    // Bit masks
    static int FLAG_BITMASK = 0x7;

    static int OP_BITPOS = 3;
    static int OP_BITMASK = (0x7 << OP_BITPOS);

    static int CTYPE_BITPOS = 6;
    static int CTYPE_BITMASK = (0x1 << CTYPE_BITPOS);
    

    // Upper payload limit (10kb)
    static final int PAYLOAD_MAX_LIMIT = 0xFFFF - HEADER_SIZE;
	
    private ByteBuffer m_bytes;
	
    public Frame(int channelPtr,
                 int ctype,
                 int op,
                 int flag,
                 ByteBuffer data) {
        super();
		
        short length = HEADER_SIZE;
		
        if (data != null) {
            if (data.capacity() > PAYLOAD_MAX_LIMIT) {
                throw new IllegalArgumentException("Payload max limit reached");
            } else {
                length += (short)(data.capacity());
            }
        }
		
        m_bytes = ByteBuffer.allocate(length + 2);
        m_bytes.order(ByteOrder.BIG_ENDIAN);
		
        m_bytes.putShort(length);
        m_bytes.putInt(channelPtr);
        m_bytes.put((byte)((ctype << CTYPE_BITPOS) | (op << OP_BITPOS) | flag));
		
        if (data != null) {
            m_bytes.put(data);
        }

        m_bytes.flip();
    }
	
    public static Frame create(int channelPtr, int ctype, int op, int flag) {
        return new Frame(channelPtr, ctype, op, flag, null);
    }

    public static Frame create(int channelPtr,
                               int ctype,
                               int op,
                               int flag,
                               ByteBuffer data) {
        return new Frame(channelPtr, ctype, op, flag, data);
    }
	
    ByteBuffer getData() {
        return m_bytes;
    }
}
