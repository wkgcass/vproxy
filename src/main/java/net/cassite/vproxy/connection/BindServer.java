package net.cassite.vproxy.connection;

import net.cassite.vproxy.app.Config;
import net.cassite.vproxy.selector.PeriodicEvent;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.Utils;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NetworkChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public class BindServer implements NetFlowRecorder {
    // a dummy connection holder for udp
    // since udp does not have connection
    // we create one for convenience
    class UDPConn {
        final InetSocketAddress remote;
        final Connection connection;
        final ConnectionHandlerContext cctx;

        // this counter will increase every time the remote sends a message
        // and clear event will remote the counter
        // if clear event find that this count is 0
        // it means timeout, and this entry will be dropped
        int counter = 0;
        final PeriodicEvent clearEvent;

        UDPConn(InetSocketAddress remote, Connection connection, ConnectionHandlerContext cctx) {
            this.remote = remote;
            this.connection = connection;
            this.cctx = cctx;
            if (_eventLoop != null) {
                this.clearEvent = _eventLoop.getSelectorEventLoop().period(Config.udpTimeout, () -> {
                    if (counter == 0) {
                        // should drop the entry
                        remove();
                        // fire closed event
                        cctx.handler.closed(cctx);
                    }
                });
            } else {
                this.clearEvent = null;
            }
        }

        void cancel() {
            if (this.clearEvent != null) {
                this.clearEvent.cancel();
            }
        }

        public void remove() {
            cancel();
            udpDummyConnMap.remove(this.remote);
        }
    }

    public final InetSocketAddress bind;
    private final String _id;
    final SelectableChannel channel;
    final Protocol protocol;

    // this field is only for udp
    // the field will be accessed from only one connection (the event loop)
    final Map<InetSocketAddress, UDPConn> udpDummyConnMap = new HashMap<>(65536); // make it large

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
            return new BindServer(Protocol.TCP, channel);
        } catch (IOException e) {
            channel.close(); // close the channel if create BindServer failed
            throw e;
        }
    }

    public static BindServer createUDP(InetSocketAddress bindAddress) throws IOException {
        DatagramChannel channel = DatagramChannel.open(
            (bindAddress.getAddress() instanceof Inet6Address)
                ? StandardProtocolFamily.INET6
                : StandardProtocolFamily.INET
        );
        channel.configureBlocking(false);
        channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        channel.bind(bindAddress);
        try {
            return new BindServer(Protocol.UDP, channel);
        } catch (IOException e) {
            channel.close(); // close the channel if create BindServer failed
            throw e;
        }
    }

    private BindServer(Protocol protocol, NetworkChannel channel) throws IOException {
        this.protocol = protocol;
        assert (protocol == Protocol.TCP && channel instanceof ServerSocketChannel)
            || (protocol == Protocol.UDP && channel instanceof DatagramChannel);

        this.channel = (SelectableChannel) channel;
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

        // clear after events removed from loop
        for (UDPConn c : udpDummyConnMap.values()) {
            c.cancel();
        }
        udpDummyConnMap.clear();

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
