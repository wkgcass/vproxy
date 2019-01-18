package net.cassite.vproxy.example;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class EchoClient {
    public static void main(String[] args) throws IOException, InterruptedException {
        runBlock(19083);
    }

    public static void runBlock(int port) throws IOException, InterruptedException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", port));
        OutputStream output = socket.getOutputStream();
        InputStream input = socket.getInputStream();
        byte[] buffer = new byte[32];
        for (int i = 0; i < 10; ++i) { // demonstrate for 10 times
            int strLen = (int) (Math.random() * 23) + 3;
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < strLen; ++c) {
                char cc = (char) ('A' + c);
                sb.append(cc);
            }
            String toSend = sb.toString();
            System.out.println("client send: \033[1;35m" + toSend + "\033[0m");
            output.write(toSend.getBytes());
            //noinspection ResultOfMethodCallIgnored
            int total = 0;
            while (total < strLen) {
                int l = input.read(buffer);
                System.out.println("client receive: \033[1;36m" + new String(buffer, 0, l, StandardCharsets.UTF_8) + "\033[0m");
                total += l;
            }

            Thread.sleep(1000);
        }
        socket.close();
        Thread.sleep(1000);
    }
}
