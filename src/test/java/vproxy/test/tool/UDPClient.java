package vproxy.test.tool;

import vfd.IP;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

public class UDPClient {
    private final int port;
    public DatagramSocket socket;

    public UDPClient(int port) {
        this.port = port;
    }

    public void connect() throws IOException {
        socket = new DatagramSocket();
        socket.connect(IP.from("127.0.0.1").toInetAddress(), port);
    }

    public String sendAndRecv(String data) throws IOException {
        byte[] bytes = data.getBytes();
        socket.send(new DatagramPacket(bytes, 0, bytes.length));
        byte[] buf = new byte[2048];
        DatagramPacket p = new DatagramPacket(buf, 0, buf.length);
        socket.receive(p);
        return new String(buf, 0, p.getLength(), StandardCharsets.UTF_8);
    }

    public void close() {
        socket.close();
    }
}
