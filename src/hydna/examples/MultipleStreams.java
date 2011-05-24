package hydna.examples;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import hydna.Stream;
import hydna.StreamData;
import hydna.StreamError;
import hydna.StreamMode;

/**
 *  Multiple streams example
 */
public class MultipleStreams {
	public static void main(String[] args) throws CharacterCodingException, StreamError, InterruptedException {
		Stream stream = new Stream();
	    stream.connect("localhost/x11221133", StreamMode.READWRITE);
	    
	    Stream stream2 = new Stream();
	    stream2.connect("localhost/x3333", StreamMode.READWRITE);
	
	    while(!stream.isConnected()) {
	        stream.checkForStreamError();
	        Thread.sleep(1000);
	    }
	    
	    while(!stream2.isConnected()) {
	        stream2.checkForStreamError();
	        Thread.sleep(1000);
	    }
	
	    stream.writeString("Hello");
	    stream2.writeString("World");
	
	    for (;;) {
	        if (!stream.isDataEmpty()) {
	            StreamData data = stream.popData();
	            ByteBuffer payload = data.getContent();
	
	            Charset charset = Charset.forName("US-ASCII");
            	CharsetDecoder decoder = charset.newDecoder();
				
            	String m = decoder.decode(payload).toString();
				
            	System.out.println(m);
            	
	            break;
	        } else {
	            stream.checkForStreamError();
	        }
	    }
	    
	    for (;;) {
	        if (!stream2.isDataEmpty()) {
	            StreamData data = stream2.popData();
	            ByteBuffer payload = data.getContent();
	
	            Charset charset = Charset.forName("US-ASCII");
            	CharsetDecoder decoder = charset.newDecoder();
				
            	String m = decoder.decode(payload).toString();
				
            	System.out.println(m);
            	
	            break;
	        } else {
	            stream2.checkForStreamError();
	        }
	    }
	    
	    stream.close();
	    stream2.close();
	}
}
