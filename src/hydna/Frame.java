package hydna;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Frame {
	public static final short HEADER_SIZE = 0x07;
	
	// Opcodes
	public static final int NOOP   = 0x00;
    public static final int OPEN   = 0x01;
    public static final int DATA   = 0x02;
    public static final int SIGNAL = 0x03;
    
    // Open Flags
    public static final int OPEN_ALLOW = 0x0;
    public static final int OPEN_REDIRECT = 0x1;
    public static final int OPEN_DENY = 0x7;

    // Signal Flags
    public static final int SIG_EMIT = 0x0;
    public static final int SIG_END = 0x1;
    public static final int SIG_ERROR = 0x7;
    
    // Upper payload limit (10kb)
    public static final int PAYLOAD_MAX_LIMIT = 10 * 1024;
	
	private ByteBuffer m_bytes;
	
	public Frame(int ch, int op, int flag, ByteBuffer payload) {
		super();
		
		short length = HEADER_SIZE;
		
		if (payload != null) {
			if (payload.capacity() > PAYLOAD_MAX_LIMIT) {
				throw new IllegalArgumentException("Payload max limit reached");
			} else {
				length += (short)(payload.capacity());
			}
		}
		
		m_bytes = ByteBuffer.allocate(length);
		m_bytes.order(ByteOrder.BIG_ENDIAN);
		
		m_bytes.putShort(length);
		m_bytes.putInt(ch);
		m_bytes.put((byte) ((op & 3) << 3 | (flag & 7)));
		
		if (payload != null) {
			m_bytes.put(payload);
		}
		
		m_bytes.flip();
	}
	
	public Frame(int ch, int op, int flag) {
		this(ch, op, flag, null);
	}
	
	public ByteBuffer getData() {
		return m_bytes;
	}
	
	void setChannel (int value) {
		m_bytes.putInt(3, value);
	}
}
