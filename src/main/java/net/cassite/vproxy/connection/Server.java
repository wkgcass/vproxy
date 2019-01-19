package net.cassite.vproxy.connection;

import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

public class Server {
    public final InetSocketAddress bind;
    private final String _id;
    final ServerSocketChannel channel;

    NetEventLoop eventLoop = null;

    private boolean closed;

    public Server(ServerSocketChannel channel) throws IOException {
        this.channel = channel;
        bind = (InetSocketAddress) channel.getLocalAddress();
        _id = Utils.ipStr(bind.getAddress().getAddress()) + ":" + bind.getPort();
    }

    public boolean isClosed() {
        return closed;
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
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

    public String id() {
        return _id;
    }

    @Override
    public String toString() {
        return "Server(" + id() + ")[" + (closed ? "closed" : "open") + "]";
    }
}
