package hydna.examples;

import hydna.Stream;
import hydna.StreamMode;

public class SpeedTest {
	static final int NO_BROADCASTS = 100000;
	static final String CONTENT = "fjhksdffkhjfhjsdkahjkfsadjhksfjhfsdjhlasfhjlksadfhjldaljhksfadjhsfdahjsljhdfjlhksfadlfsjhadljhkfsadjlhkajhlksdfjhlljhsa";

	public static void main(String[] args) {
	    if (args.length != 1) {
	        System.err.println("Usage: java " + SpeedTest.class.getName() + " {receive|send}");
	        return;
	    }

	    int i = 0;
	    
	    try { 
	        String arg = args[0];

	        Stream stream = new Stream();
	        stream.connect("localhost/x11221133", StreamMode.READWRITE);

	        while(!stream.isConnected()) {
	            stream.checkForStreamError();
	            Thread.sleep(1000);
	        }
	        
	        long time = 0;

	        if (arg.equals("receive")) {
	            System.out.println("Receiving from x11221133");

	            for(;;) {
	                if (!stream.isDataEmpty()) {
	                    stream.popData();

	                    if (i == 0) {
	                        time = System.nanoTime();
	                    }
	                
	                    ++i;

	                    if (i == NO_BROADCASTS) {
	                        time = System.nanoTime() - time;
	                        System.out.println("Received " + NO_BROADCASTS + " packets");
	                        System.out.println("Time: " + time/1000000 + "ms");
	                        i = 0;
	                    }
	                } else {
	                    stream.checkForStreamError();
	                }
	            }
	        } else if (arg.equals("send")) {
	            System.out.println("Sending " + NO_BROADCASTS + " packets to x11221133");

	            time = System.nanoTime();

	            for (i = 0; i < NO_BROADCASTS; i++) {
	                stream.writeString(CONTENT);
	            }

	            time = System.nanoTime() - time;

	            System.out.println("Time: " + time/1000000 + "ms");

	            i = 0;
	            while(i < NO_BROADCASTS) {
	                if (!stream.isDataEmpty()) {
	                    stream.popData();
	                    i++;
	                } else {
	                    stream.checkForStreamError();
	                }
	            }
	        } else {
	            System.out.println("Usage: java " + SpeedTest.class.getName() + " {receive|send}");
	            return;
	        }

	        stream.close();
	    } catch (Exception e) {
	        System.out.println("Caught exception (i=" + i + "): " + e.getMessage());
	    }

	    return;
	}
}
