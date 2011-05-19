# Usage

In the following example we open a read/write stream, send a "Hello world!"
when the connection has been established and print all received messages to
the console.

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
    * Hello world example
    */
    public class HelloWorld {
        public static void main(String[] args) throws
                CharacterCodingException, StreamError,
                InterruptedException {
            Stream stream = new Stream();
            stream.connect("localhost/x11221133", StreamMode.READWRITE);

            while(!stream.isConnected()) {
                stream.checkForStreamError();
                Thread.sleep(1000);
            }

            stream.writeString("Hello World");

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
            stream.close();
        }
    }
