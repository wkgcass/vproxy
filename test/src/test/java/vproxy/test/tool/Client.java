package vproxy.test.tool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Client {
    private final int port;
    public Socket socket;

    public Client(int port) {
        this.port = port;
    }

    public void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", port));
    }

    public String sendAndRecv(String data, int recvLen) throws IOException {
        StringBuilder sb = new StringBuilder();

        socket.getOutputStream().write(data.getBytes());
        byte[] buf = new byte[4096];

        while (true) {
            int len = socket.getInputStream().read(buf);
            if (len == -1) {
                if (recvLen <= 0) {
                    return "";
                }
                throw new IOException("remote closed");
            }
            String s = new String(buf, 0, len, StandardCharsets.UTF_8);
            sb.append(s);
            recvLen -= len;
            if (recvLen == 0)
                return sb.toString();
        }
    }

    public void close() throws IOException {
        socket.close();
    }
}
