package hydna.examples;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import hydna.Stream;
import hydna.StreamError;
import hydna.StreamMode;
import hydna.StreamSignal;

/**
 *  Signal example
 */
public class Signals {
	public static void main(String[] args) throws CharacterCodingException, StreamError, InterruptedException {
		Stream stream = new Stream();
	    stream.connect("localhost/x00112233", StreamMode.READWRITE_EMIT);

	    while(!stream.isConnected()) {
	        stream.checkForStreamError();
	        Thread.sleep(1000);
	    }

	    stream.emitString("ping");

	    for (;;) {
	        if (!stream.isSignalEmpty()) {
	            StreamSignal signal = stream.popSignal();
	            ByteBuffer payload = signal.getContent();

	            Charset charset = Charset.forName("US-ASCII");
            	CharsetDecoder decoder = charset.newDecoder();
				
            	String m = decoder.decode(payload).toString();
				
            	System.out.println(m);
	            
	            break;
	        } else {
	            stream.checkForStreamError();
	        }
	    }
	    stream.close();
	}
}
