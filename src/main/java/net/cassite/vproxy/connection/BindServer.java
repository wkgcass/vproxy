package net.cassite.vproxy.connection;

import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.atomic.LongAdder;

public class BindServer implements NetFlowRecorder {
    public final InetSocketAddress bind;
    private final String _id;
    public final SelectableChannel channel;

    // statistics
    private final LongAdder fromRemoteBytes = new LongAdder();
    private final LongAdder toRemoteBytes = new LongAdder();
    private long historyAcceptedConnectionCount = 0; // no concurrency when accepting connections

    NetEventLoop _eventLoop = null;

    private boolean closed;

    public static BindServer create(InetSocketAddress bindAddress) throws IOException {
        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.configureBlocking(false);
        channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
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

    // --- START statistics ---
    public long getFromRemoteBytes() {
        return fromRemoteBytes.longValue();
    }

    public long getToRemoteBytes() {
        return toRemoteBytes.longValue();
    }

    @Override
    public void incFromRemoteBytes(long bytes) {
        fromRemoteBytes.add(bytes);
    }

    @Override
    public void incToRemoteBytes(long bytes) {
        toRemoteBytes.add(bytes);
    }

    public void incHistoryAcceptedConnectionCount() {
        ++historyAcceptedConnectionCount;
    }

    public long getHistoryAcceptedConnectionCount() {
        return historyAcceptedConnectionCount;
    }
    // --- END statistics ---

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
