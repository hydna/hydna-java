# Usage

In the following example we open a read/write channel, send a "Hello world!"
when the connection has been established and print all received messages to
the console.

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
    * Hello world example
    */
    public class HelloWorld {
        public static void main(String[] args) throws
                CharacterCodingException, ChannelError,
                InterruptedException {
            Channel channel = new Channel();
            channel.connect("localhost/x11221133", ChannelMode.READWRITE);

            while(!channel.isConnected()) {
                channel.checkForChannelError();
                Thread.sleep(1000);
            }

            channel.writeString("Hello World");

            for (;;) {
                if (!channel.isDataEmpty()) {
                    ChannelData data = channnel.popData();
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
