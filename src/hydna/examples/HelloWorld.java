package com.hydna.examples;

import com.hydna.Channel;
import com.hydna.ChannelEvent;
import com.hydna.ChannelData;
import com.hydna.ChannelError;
import com.hydna.ChannelMode;

/**
 *  Hello world example
 */
public class HelloWorld {
    public static void main(String[] args)
        throws ChannelError, InterruptedException {

        Channel channel = new Channel();
        channel.connect("public.hydna.net", ChannelMode.READWRITE);

        // Send a "hello world" to public domain
        channel.send("Hello world from java");

        // The method "nextEvent()" is blocking. See "Listener.java"
        // for an example how to receive without blocking, using
        // the method "hasEvents()".
        ChannelEvent event = channel.nextEvent();
        System.out.println(event.getString());

        // Close the channel, which terminates the underlying
        // receive-loop.
        channel.close();
    }
}
