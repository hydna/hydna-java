package hydna;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Packet {
	public static final short HEADER_SIZE = 0x08;
	
	// Opcodes
    public static final int OPEN   = 0x01;
    public static final int DATA   = 0x02;
    public static final int SIGNAL = 0x03;

    // Handshake flags
    public static final int HANDSHAKE_UNKNOWN = 0x01;
    public static final int HANDSHAKE_SERVER_BUSY = 0x02;
    public static final int HANDSHAKE_BADFORMAT = 0x03;
    public static final int HANDSHAKE_HOSTNAME = 0x04;
    public static final int HANDSHAKE_PROTOCOL = 0x05;
    public static final int HANDSHAKE_SERVER_ERROR = 0x06;

    // Open Flags
    public static final int OPEN_SUCCESS = 0x0;
    public static final int OPEN_REDIRECT = 0x1;
    public static final int OPEN_FAIL_NA = 0x8;
    public static final int OPEN_FAIL_MODE = 0x9;
    public static final int OPEN_FAIL_PROTOCOL = 0xa;
    public static final int OPEN_FAIL_HOST = 0xb;
    public static final int OPEN_FAIL_AUTH = 0xc;
    public static final int OPEN_FAIL_SERVICE_NA = 0xd;
    public static final int OPEN_FAIL_SERVICE_ERR = 0xe;
    public static final int OPEN_FAIL_OTHER = 0xf;

    // Signal Flags
    public static final int SIG_EMIT = 0x0;
    public static final int SIG_END = 0x1;
    public static final int SIG_ERR_PROTOCOL = 0xa;
    public static final int SIG_ERR_OPERATION = 0xb;
    public static final int SIG_ERR_LIMIT = 0xc;
    public static final int SIG_ERR_SERVER = 0xd;
    public static final int SIG_ERR_VIOLATION = 0xe;
    public static final int SIG_ERR_OTHER = 0xf;
    
    // Upper payload limit (10kb)
    public static final int PAYLOAD_MAX_LIMIT = 10 * 1024;
	
	private ByteBuffer m_bytes;
	
	public Packet(int addr, int op, int flag, ByteBuffer payload) {
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
		m_bytes.put((byte) 0); // Reserved
		m_bytes.putInt(addr);
		m_bytes.put((byte) (op << 4 | flag));
		
		if (payload != null) {
			m_bytes.put(payload);
		}
		
		m_bytes.flip();
	}
	
	public ByteBuffer getData() {
		return m_bytes;
	}
}
