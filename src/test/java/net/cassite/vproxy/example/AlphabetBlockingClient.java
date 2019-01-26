package net.cassite.vproxy.example;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class AlphabetBlockingClient {
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
        byte[] buffer = new byte[32];
        for (int i = 0; i < times; ++i) { // demonstrate for 10 times
            int strLen = (int) (Math.random() * 23) + 3;
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < strLen; ++c) {
                char cc = (char) ('A' + c);
                sb.append(cc);
            }
            String toSend = sb.toString();
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
            StringBuilder recv = new StringBuilder();
            int total = 0;
            while (total < strLen) {
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
                String recv0 = new String(buffer, 0, l, StandardCharsets.UTF_8);
                System.out.println("client receive: \033[1;36m" + recv0 + "\033[0m");
                total += l;
                recv.append(recv0);
            }
            if (!recv.toString().equals(toSend)) {
                throw new RuntimeException("sending " + toSend + " but receive " + recv + ", mismatch");
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
