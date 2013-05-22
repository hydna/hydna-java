package hydna.examples;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import hydna.Channel;
import hydna.ChannelData;
import hydna.ChannelError;
import hydna.ChannelMode;

/**
 *  Listener example
 */
public class Listener {
	public static void main(String[] args) throws CharacterCodingException, ChannelError, InterruptedException {
		Channel channel = new Channel();
	    channel.connect("public.hydna.net/1", ChannelMode.READWRITE);

	    while(!channel.isConnected()) {
	        channel.checkForChannelError();
	        Thread.sleep(1000);
	    }

	    for (;;) {
	        if (!channel.isDataEmpty()) {
	            ChannelData data = channel.popData();
	            ByteBuffer payload = data.getContent();

	            Charset charset = Charset.forName("US-ASCII");
            	CharsetDecoder decoder = charset.newDecoder();
				
            	String m = decoder.decode(payload).toString();
				
            	System.out.println(m);
	        } else {
	            channel.checkForChannelError();
	        }
	    }
	}
}
