package hydna;

import java.nio.ByteBuffer;

/**
 *  This class is used internally by both the Channel and the Connection class.
 *  A user of the library should not create an instance of this class.
 */
public class OpenRequest {
    private Channel m_channel;
    private int m_channelPtr;
    private int m_mode;
    private ByteBuffer m_token;
	
    public OpenRequest(Channel channel, int ch, int mode, ByteBuffer token) {
        m_channel = channel;
        m_channelPtr = ch;
        m_mode = mode;
        m_token = token;
    }
	
    public Channel getChannel() {
        return m_channel;
    }
	
    public int getChannelId() {
        return m_channelPtr;
    }
	
    public Frame getFrame() {
        return Frame.create(
            m_channelPtr,
            ContentType.UTF8,
            Frame.OPEN,
            (byte)m_mode,
            m_token
        );
    }	
}
