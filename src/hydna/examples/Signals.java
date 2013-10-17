package hydna.examples;

import java.nio.ByteBuffer;

import hydna.Channel;
import hydna.ChannelError;
import hydna.ChannelMode;
import hydna.ChannelEvent;
import hydna.ChannelSignal;

/**
 *  Signal example
 */
public class Signals {
    public static void main(String[] args)
        throws ChannelError, InterruptedException {

        Channel channel = new Channel();
        String url = "public.hydna.net/ping-back";
        channel.connect(url, ChannelMode.READWRITEEMIT);

        // Send an emit with message "ping" to server
        channel.emit("ping");

        // Wait for a PONG response
        ChannelEvent event = channel.nextEvent();

        // Check that it was a Signal that we really got
        if (event instanceof ChannelSignal) {
            System.out.println(event.getString());
        } else {
            System.out.println("Expected a pong response");
        }

        channel.close();
    }
}