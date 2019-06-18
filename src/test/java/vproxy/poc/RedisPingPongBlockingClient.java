package vproxy.poc;

import vproxy.redis.RESPParser;
import vproxy.util.ByteArrayChannel;
import vproxy.util.RingBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class RedisPingPongBlockingClient {
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

        RingBuffer rb = RingBuffer.allocate(64);
        byte[] buffer = new byte[64];
        for (int i = 0; i < times; ++i) { // demonstrate for 10 times
            RESPParser parser = new RESPParser(64);
            int strLen = (int) (Math.random() * 30) - 6; // about a quarter chance to be < 0
            if (strLen < 0)
                strLen = 0;
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < strLen; ++c) {
                char cc = (char) ('A' + c);
                sb.append(cc);
            }
            double rand = Math.random();
            String toSend;
            if (rand < 0.25) {
                // use array, standard redis-cli request format
                if (strLen == 0) {
                    toSend = "*1\r\n$4\r\nPING\r\n";
                } else {
                    toSend = "*2\r\n$4\r\nPING\r\n$" + sb.length() + "\r\n" + sb.toString() + "\r\n";
                }
            } else if (rand < 0.5) {
                // use bulk str
                if (strLen == 0) {
                    toSend = "$4\r\nPING\r\n";
                } else {
                    toSend = "$" + (4 + 1 + sb.length()) + "\r\nPING " + sb.toString() + "\r\n";
                }
            } else if (rand < 0.75) {
                // use inline
                if (strLen == 0) {
                    toSend = "PING\r\n";
                } else {
                    toSend = "PING " + sb.toString() + "\r\n";
                }
            } else {
                // use simple
                if (strLen == 0) {
                    toSend = "+PING\r\n";
                } else {
                    toSend = "+PING " + sb.toString() + "\r\n";
                }
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
