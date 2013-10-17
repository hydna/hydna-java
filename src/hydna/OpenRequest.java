package com.hydna;

import java.nio.ByteBuffer;

/**
 *  This class is used internally by both the Channel and the Connection class.
 *  A user of the library should not create an instance of this class.
 */
class OpenRequest {
    private Channel m_channel;
    private ByteBuffer m_path;
    private int m_channelPtr;
    private int m_mode;
    private ByteBuffer m_token;
	
    public OpenRequest(Channel channel,
                       ByteBuffer path,
                       int mode,
                       ByteBuffer token) {
        m_channel = channel;
        m_path = path;
        m_mode = mode;
        m_token = token;
    }

    Channel getChannel() {
        return m_channel;
    }

    int getChannelPtr() {
        return m_channelPtr;
    }

    void setChannelPtr(int channelPtr) {
        m_channelPtr = channelPtr;
    }

    ByteBuffer getPath() {
        return m_path;
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

    public Frame getResolveFrame() {
        return Frame.create(
            0,
            ContentType.UTF8,
            Frame.RESOLVE,
            0,
            m_path
        );
    }	
}
