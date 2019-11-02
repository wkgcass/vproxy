package vproxy.poc;

import vproxy.redis.RESPParser;
import vproxy.util.RingBuffer;
import vproxy.util.nio.ByteArrayChannel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class RedisIncBlockingClient {
    public static void main(String[] args) throws IOException, InterruptedException {
        runBlock(19080, 10, false);
    }

    @SuppressWarnings("Duplicates")
    public static void runBlock(int port, int times, boolean closeEveryTime) throws IOException, InterruptedException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", port));
        } catch (IOException e) {
            System.err.println("connect failed: " + e);
            socket.close();
            Thread.sleep(1000);
            runBlock(port, times, closeEveryTime);
            return;
        }
        OutputStream output = socket.getOutputStream();
        InputStream input = socket.getInputStream();

        String key = "the-key";
        RingBuffer rb = RingBuffer.allocate(64);
        byte[] buffer = new byte[64];
        for (int i = 0; i < times; ++i) { // demonstrate for 10 times
            RESPParser parser = new RESPParser(64);
            double rand = Math.random();
            String toSend;
            if (rand < 0.25) {
                // use array, standard redis-cli request format
                toSend = "*2\r\n$4\r\nINCR\r\n$" + key.length() + "\r\n" + key + "\r\n";
            } else if (rand < 0.5) {
                // use bulk str
                toSend = "$" + (5 + key.length()) + "\r\nINCR " + key + "\r\n";
            } else if (rand < 0.75) {
                // use inline
                toSend = "INCR " + key + "\r\n";
            } else {
                // use simple
                toSend = "+INCR " + key + "\r\n";
            }
            System.out.println("client send: \033[1;35m" + toSend + "\033[0m");
            try {
                output.write(toSend.getBytes());
            } catch (IOException e) {
                System.err.println("write failed: " + e);
                socket.close();
                Thread.sleep(1000);
                runBlock(port, times - i, closeEveryTime);
                return;
            }
            while (true) {
                int l;
                try {
                    l = input.read(buffer);
                } catch (IOException e) {
                    System.err.println("read failed: " + e);
                    socket.close();
                    Thread.sleep(1000);
                    runBlock(port, times - i, closeEveryTime);
                    return;
                }
                if (l < 0) {
                    System.err.println("remote write closed");
                    socket.close();
                    Thread.sleep(1000);
                    runBlock(port, times - i, closeEveryTime);
                    return;
                }
                if (l == 0)
                    continue;
                byte[] buf = new byte[l];
                System.arraycopy(buffer, 0, buf, 0, l);
                ByteArrayChannel chnl = ByteArrayChannel.fromFull(buf);
                rb.storeBytesFrom(chnl);
                int r = parser.feed(rb);
                String recv;
                if (r == 0) {
                    recv = (String) parser.getResult().getJavaObject();
                } else {
                    String errMsg = parser.getErrorMessage();
                    if (errMsg != null)
                        throw new RuntimeException("parse failed " + errMsg);
                    continue;
                }

                System.out.println("client receive: \033[1;36m" + recv + "\033[0m");
                break;
            }

            Thread.sleep(500);

            if (closeEveryTime) {
                socket.close();
                runBlock(port, times - 1, true);
                return;
            }
        }
        socket.close();
        Thread.sleep(1000);
    }
}
