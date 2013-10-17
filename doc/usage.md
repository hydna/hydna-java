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
    import hydna.ChannelEvent;
    import hydna.ChannelError;
    import hydna.ChannelMode;

    /**
    * Hello world example
    */
    public class HelloWorld {
        public static void main(String[] args)
            throws ChannelError, InterruptedException {

            Channel channel = new Channel();
            channel.connect("public.hydna.net", ChannelMode.READWRITE);

            channel.send("Hello World");
            ChannelEvent event = channel.nextEvent();

            System.our.println(event.getString());

            channel.close();
        }
    }
