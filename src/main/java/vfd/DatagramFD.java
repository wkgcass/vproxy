package vfd;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public interface DatagramFD extends FD, ReadableByteChannel, WritableByteChannel {
    void connect(InetSocketAddress l4addr) throws IOException;

    void bind(InetSocketAddress l4addr) throws IOException;

    int send(ByteBuffer buf, InetSocketAddress remote) throws IOException;

    SocketAddress receive(ByteBuffer buf) throws IOException;

    SocketAddress getLocalAddress() throws IOException;

    SocketAddress getRemoteAddress() throws IOException;
}
