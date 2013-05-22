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
 *  Multiple channels example
 */
public class MultipleChannels {
	public static void main(String[] args) throws CharacterCodingException, ChannelError, InterruptedException {
		Channel channel = new Channel();
	    channel.connect("public.hydna.net/1", ChannelMode.READWRITE);
	    
	    Channel channel2 = new Channel();
	    channel2.connect("public.hydna.net/2", ChannelMode.READWRITE);
	
	    while(!channel.isConnected()) {
	        channel.checkForChannelError();
	        Thread.sleep(1000);
	    }
	    
	    while(!channel2.isConnected()) {
	        channel2.checkForChannelError();
	        Thread.sleep(1000);
	    }
	
	    channel.writeString("Hello");
	    channel2.writeString("World");
	
	    for (;;) {
	        if (!channel.isDataEmpty()) {
	            ChannelData data = channel.popData();
	            ByteBuffer payload = data.getContent();
	
	            Charset charset = Charset.forName("US-ASCII");
            	CharsetDecoder decoder = charset.newDecoder();
				
            	String m = decoder.decode(payload).toString();
				
            	System.out.println(m);
            	
	            break;
	        } else {
	            channel.checkForChannelError();
	        }
	    }
	    
	    for (;;) {
	        if (!channel2.isDataEmpty()) {
	            ChannelData data = channel2.popData();
	            ByteBuffer payload = data.getContent();
	
	            Charset charset = Charset.forName("US-ASCII");
            	CharsetDecoder decoder = charset.newDecoder();
				
            	String m = decoder.decode(payload).toString();
				
            	System.out.println(m);
            	
	            break;
	        } else {
	            channel2.checkForChannelError();
	        }
	    }
	    
	    channel.close();
	    channel2.close();
	}
}
