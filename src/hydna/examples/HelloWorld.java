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
 *  Hello world example
 */
public class HelloWorld {
	public static void main(String[] args) throws CharacterCodingException, ChannelError, InterruptedException {
		Channel channel = new Channel();
	    channel.connect("public.hydna.net", ChannelMode.READWRITE);
	
	    while(!channel.isConnected()) {
	        channel.checkForChannelError();
	        Thread.sleep(1000);
	    }
	    
	    String message = channel.getMessage();
	    if (!message.equals("")) {
	    	System.out.println(message);
	    }
	    
	    channel.writeString("Hello World from java");
	
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
	    channel.close();
	}
}
