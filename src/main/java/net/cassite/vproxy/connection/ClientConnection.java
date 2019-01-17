package net.cassite.vproxy.connection;

import net.cassite.vproxy.util.RingBuffer;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class ClientConnection extends Connection {
    public ClientConnection(SocketChannel channel, RingBuffer inBuffer, RingBuffer outBuffer) throws IOException {
        super(channel, inBuffer, outBuffer);
    }
}
