package hydna.examples;

import hydna.Channel;
import hydna.ChannelMode;

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

	        Channel channel = new Channel();
	        channel.connect("public.hydna.net/10", ChannelMode.READWRITE);

	        while(!channel.isConnected()) {
	            channel.checkForChannelError();
	            Thread.sleep(1000);
	        }
	        
	        long time = 0;

	        if (arg.equals("receive")) {
	            System.out.println("Receiving from channel 10");

	            for(;;) {
	                if (!channel.isDataEmpty()) {
	                    channel.popData();

	                    if (i == 0) {
	                        time = System.nanoTime();
	                    }
	                
	                    ++i;

	                    if (i == NO_BROADCASTS) {
	                        time = System.nanoTime() - time;
	                        System.out.println("Received " + NO_BROADCASTS + " frames");
	                        System.out.println("Time: " + time/1000000 + "ms");
	                        i = 0;
	                    }
	                } else {
	                    channel.checkForChannelError();
	                }
	            }
	        } else if (arg.equals("send")) {
	            System.out.println("Sending " + NO_BROADCASTS + " frames to channel 10");

	            time = System.nanoTime();

	            for (i = 0; i < NO_BROADCASTS; i++) {
	                channel.writeString(CONTENT);
	            }

	            time = System.nanoTime() - time;

	            System.out.println("Time: " + time/1000000 + "ms");

	            i = 0;
	            while(i < NO_BROADCASTS) {
	                if (!channel.isDataEmpty()) {
	                    channel.popData();
	                    i++;
	                } else {
	                    channel.checkForChannelError();
	                }
	            }
	        } else {
	            System.out.println("Usage: java " + SpeedTest.class.getName() + " {receive|send}");
	            return;
	        }

	        channel.close();
	    } catch (Exception e) {
	        System.out.println("Caught exception (i=" + i + "): " + e.getMessage());
	    }

	    return;
	}
}
