package com.hydna;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharacterCodingException;

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

    public int getCode() {
        return m_code;
    }

    static ChannelError fromOpenError(int flag, int ctype, ByteBuffer data) {
        int code = flag;
        String message = "";

        if (code < 7) {
            message = "Not allowed to open channel";
        }

        if (ctype == ContentType.UTF8 && data != null) {

            Charset charset = Charset.forName("UTF-8");
            CharsetDecoder decoder = charset.newDecoder();
            int pos = data.position();

            try {
                message = decoder.decode(data).toString();
            } catch (CharacterCodingException ex) {
            } finally {
                data.position(pos);
            }
        }

        return new ChannelError(message, flag);
    }

    static ChannelError fromSigError(int flag, int ctype, ByteBuffer data) {
        String message = "";

        message = "Bad signal";

        if (ctype == ContentType.UTF8 && data != null) {

            Charset charset = Charset.forName("UTF-8");
            CharsetDecoder decoder = charset.newDecoder();
            int pos = data.position();

            try {
                message = decoder.decode(data).toString();
            } catch (CharacterCodingException ex) {
            } finally {
                data.position(pos);
            }
        }

        return new ChannelError(message);
    }
}
