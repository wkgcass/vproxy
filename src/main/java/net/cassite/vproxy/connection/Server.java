package net.cassite.vproxy.connection;

import net.cassite.vproxy.util.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

public class Server {
    public final String localHost;
    public final int localPort;
    final ServerSocketChannel channel;

    NetEventLoop eventLoop = null;

    private boolean closed;

    public Server(ServerSocketChannel channel) throws IOException {
        this.channel = channel;
        InetSocketAddress addr = (InetSocketAddress) channel.getLocalAddress();
        localHost = addr.getHostString();
        localPort = addr.getPort();
    }

    public boolean isClosed() {
        return closed;
    }

    public void close() {
        if (eventLoop != null) {
            eventLoop.selectorEventLoop.remove(channel);
        }
        eventLoop = null;
        try {
            channel.close();
        } catch (IOException e) {
            // we can do nothing about it
            Logger.stderr("got error when closing server channel " + e);
        }
    }
}
