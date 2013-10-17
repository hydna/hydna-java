package com.hydna.examples;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import com.hydna.Channel;
import com.hydna.ChannelEvent;
import com.hydna.ChannelData;
import com.hydna.ChannelError;
import com.hydna.ChannelMode;

/**
 *  Listener example
 */
public class Listener {
    public static void main(String[] args)
        throws CharacterCodingException, ChannelError, InterruptedException {

        ChannelEvent event;

        Channel channel = new Channel();
        event = channel.connect("public.hydna.net", ChannelMode.READWRITE);

        // Check if channel sent a welcome message on open.
        if (event != null) {
            System.out.println("[WELCOME]: " + event.getString());
        }

        System.out.println("Press Ctrl-C to abort the receive loop");

        for (;;) {
            if (channel.hasEvents()) {
                event = channel.nextEvent();
                if (event instanceof ChannelData) {
                    if (event.isUtf8Content()) {
                        System.out.println("[DATA]" + event.getString());
                    } else {
                        System.out.println("[DATA] <binary>");
                    }
                } else {
                    if (event.isUtf8Content()) {
                        System.out.println("[SIGNAL]" + event.getString());
                    } else {
                        System.out.println("[SIGNAL] <binary>");
                    }
                }
            }
            Thread.sleep(1);
        }
    }
}
