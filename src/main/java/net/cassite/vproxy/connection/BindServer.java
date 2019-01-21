package net.cassite.vproxy.connection;

import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

public class BindServer {
    public final InetSocketAddress bind;
    private final String _id;
    final ServerSocketChannel channel;

    NetEventLoop _eventLoop = null;

    private boolean closed;

    public static BindServer create(InetSocketAddress bindAddress) throws IOException {
        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.configureBlocking(false);
        channel.bind(bindAddress);
        try {
            return new BindServer(channel);
        } catch (IOException e) {
            channel.close(); // close the channel if create BindServer failed
            throw e;
        }
    }

    private BindServer(ServerSocketChannel channel) throws IOException {
        this.channel = channel;
        bind = (InetSocketAddress) channel.getLocalAddress();
        _id = Utils.ipStr(bind.getAddress().getAddress()) + ":" + bind.getPort();
    }

    public boolean isClosed() {
        return closed;
    }

    // make it synchronized to prevent fields being inconsistent
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        NetEventLoop eventLoop = _eventLoop;
        if (eventLoop != null) {
            eventLoop.removeServer(this);
        }
        _eventLoop = null;
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
        return "BindServer(" + id() + ")[" + (closed ? "closed" : "open") + "]";
    }
}
