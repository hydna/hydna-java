package hydna;

public class ChannelError extends Exception {
	private static final long serialVersionUID = -7144874937032709941L;
	private int m_code;

	public ChannelError(String message, int code) {
		super(message);
		m_code = code;
	}
	
	public ChannelError(String message) {
		this(message, -1);
	}
	
	public static ChannelError fromOpenError(int flag, String data) {
		int code = flag;
        String msg = "";

        if (code < 7)
        	msg = "Not allowed to open channel";

        if (data != "" || data.length() != 0) {
            msg = data;
        }

        return new ChannelError(msg, code);
	}
	
	public static ChannelError fromSigError(int flag, String data) {
		int code = flag;
        String msg = "";

        if (code == 0)
        	msg = "Bad signal";

        if (data != "" || data.length() != 0) {
            msg = data;
        }

        return new ChannelError(msg, code);
	}
	
	public int getCode() {
		return m_code;
	}
}
