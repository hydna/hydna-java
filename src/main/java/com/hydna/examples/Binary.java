package com.hydna.examples;

import java.nio.ByteBuffer;

import com.hydna.Channel;
import com.hydna.ChannelEvent;
import com.hydna.ChannelData;
import com.hydna.ChannelError;
import com.hydna.ChannelMode;

/**
 *  Binary example
 */
public class Binary {
    public static void main(String[] args)
        throws ChannelError, InterruptedException {

        Channel channel = new Channel();
        channel.connect("public.hydna.net", ChannelMode.READWRITE);

        // Create a new buffer with the bytes [1, 2, 3]
        ByteBuffer buffer = ByteBuffer.allocate(3);
        buffer.put(0, (byte)1);
        buffer.put(1, (byte)2);
        buffer.put(2, (byte)3);

        // Send buffer to public domain
        channel.send(buffer);

        // The method "nextEvent()" is blocking. See "Listener.java"
        // for an example how to receive without blocking, using
        // the method "hasEvents()".
        ChannelEvent event = channel.nextEvent();

        // Prints "true"
        System.out.println("Is binary data " + event.isBinaryContent());

        ByteBuffer data = event.getData();

        System.out.println(data.get(0)); // Prints '1'
        System.out.println(data.get(1)); // Prints '2'
        System.out.println(data.get(2)); // Prints '3'

        // Close the channel, which terminates the underlying
        // receive-loop.
        channel.close();
    }
}
