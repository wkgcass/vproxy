package vfd;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public interface SocketFD extends FD, NetworkFD, ReadableByteChannel, WritableByteChannel {
    void connect(InetSocketAddress l4addr) throws IOException;

    boolean isConnected();

    void shutdownOutput() throws IOException;

    boolean finishConnect() throws IOException;
}
