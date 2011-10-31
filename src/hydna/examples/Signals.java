package hydna.examples;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import hydna.Channel;
import hydna.ChannelError;
import hydna.ChannelMode;
import hydna.ChannelSignal;

/**
 *  Signal example
 */
public class Signals {
	public static void main(String[] args) throws CharacterCodingException, ChannelError, InterruptedException {
		Channel channel = new Channel();
	    channel.connect("localhost/x00112233", ChannelMode.READWRITEEMIT);

	    while(!channel.isConnected()) {
	        channel.checkForChannelError();
	        Thread.sleep(1000);
	    }

	    channel.emitString("ping");

	    for (;;) {
	        if (!channel.isSignalEmpty()) {
	            ChannelSignal signal = channel.popSignal();
	            ByteBuffer payload = signal.getContent();

	            Charset charset = Charset.forName("US-ASCII");
            	CharsetDecoder decoder = charset.newDecoder();
				
            	String m = decoder.decode(payload).toString();
				
            	System.out.println(m);
	            
	            break;
	        } else {
	            channel.checkForChannelError();
	        }
	    }
	    channel.close();
	}
}
