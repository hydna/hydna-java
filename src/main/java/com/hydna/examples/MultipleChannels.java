package com.hydna.examples;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import com.hydna.Channel;
import com.hydna.ChannelEvent;
import com.hydna.ChannelError;
import com.hydna.ChannelMode;

/**
 *  Multiple channels example
 */
public class MultipleChannels {
    public static void main(String[] args)
        throws CharacterCodingException, ChannelError, InterruptedException {

        ChannelEvent event;

        Channel channel1 = new Channel();
        channel1.connect("public.hydna.net/channel1", ChannelMode.READWRITE);
	    
        Channel channel2 = new Channel();
        channel2.connect("public.hydna.net/channel2", ChannelMode.READWRITE);

        channel1.send("Hello");
        channel2.send("World");

        for (;;) {
            if (channel1.hasEvents()) {
                event = channel1.nextEvent();
                System.out.println(event.getString());
                channel1.close();
            }

            if (channel2.hasEvents()) {
                event = channel2.nextEvent();
                System.out.println(event.getString());
                channel2.close();
            }

            if (channel1.isConnected() == false &&
                channel2.isConnected() == false) {
                break;
            }
        }
    }
}
