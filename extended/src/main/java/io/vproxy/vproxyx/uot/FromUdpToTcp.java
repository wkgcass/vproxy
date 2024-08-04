package io.vproxy.vproxyx.uot;

import io.vproxy.base.connection.*;
import io.vproxy.base.selector.Handler;
import io.vproxy.base.selector.HandlerContext;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.ByteBufferEx;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.direct.DirectMemoryUtils;
import io.vproxy.vfd.DatagramFD;
import io.vproxy.vfd.EventSet;
import io.vproxy.vfd.FDProvider;
import io.vproxy.vfd.IPPort;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class FromUdpToTcp {
    private final NetEventLoop loop;
    private final IPPort fromIPPort;
    private final IPPort toIPPort;
    private final DatagramFD udp;

    // remote udp endpoint -> to server connection
    private final Map<IPPort, ConnectionPair> connections = new HashMap<>();

    public FromUdpToTcp(NetEventLoop loop, IPPort fromIPPort, IPPort toIPPort) throws IOException {
        this.loop = loop;
        this.fromIPPort = fromIPPort;
        this.toIPPort = toIPPort;

        udp = FDProvider.get().getProvided().openDatagramFD();
        udp.configureBlocking(false);
    }

    public void start() throws IOException {
        udp.bind(fromIPPort);
        loop.getSelectorEventLoop().add(udp, EventSet.read(), null, new UDPHandler());
    }

    private class UDPHandler implements Handler<DatagramFD> {
        private ByteBufferEx _buf = DirectMemoryUtils.allocateDirectBuffer(65536);
        private final ByteBuffer buf = _buf.realBuffer();

        @Override
        public void accept(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void connected(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void readable(HandlerContext<DatagramFD> ctx) {
            buf.limit(buf.capacity()).position(0);
            IPPort remote;
            try {
                remote = ctx.getChannel().receive(buf);
            } catch (IOException e) {
                Logger.error(LogType.SOCKET_ERROR, "failed to receive udp packet from " + fromIPPort, e);
                return;
            }
            if (remote == null) {
                // nothing read
                return;
            }
            buf.flip();
            int len = buf.limit();

            var pair = connections.get(remote);
            if (pair == null) {
                pair = new ConnectionPair();
                initPair(pair, remote);
                connections.put(remote, pair);
            } else {
                initPair(pair, remote);
            }

            Connection conn;
            if (len >= UOTUtils.LARGE_PACKET_LIMIT) {
                conn = pair.large;
            } else if (len <= UOTUtils.SMALL_PACKET_LIMIT) {
                conn = pair.small;
            } else {
                if (pair.large.getOutBuffer().free() > pair.small.getOutBuffer().free()) {
                    conn = pair.large;
                } else {
                    conn = pair.small;
                }
            }

            if (conn.getOutBuffer().free() < UOTUtils.HEADER_LEN + len) {
                assert Logger.lowLevelDebug("tcp buffer free size is " + conn.getOutBuffer().free() +
                                            ", while receiving udp packet with length " + len + " from " + remote.formatToIPPortString());
                return;
            }

            var tl = UOTUtils.buildHeader(UOTUtils.TYPE_PACKET, len);
            conn.runNoQuickWrite(c -> c.getOutBuffer().storeBytesFrom(tl));
            conn.getOutBuffer().storeBytesFrom(buf);
        }

        @Override
        public void writable(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void removed(HandlerContext<DatagramFD> ctx) {
            Logger.shouldNotHappen("uot udp server " + fromIPPort + " removed from event loop");
            try {
                udp.close();
            } catch (IOException ignore) {
            }
            if (_buf != null) {
                _buf.clean();
                _buf = null;
            }
        }
    }

    private void initPair(ConnectionPair pair, IPPort remote) {
        if (pair.small != null && pair.large != null) {
            return;
        }
        int needConn;
        if (pair.small == null && pair.large == null) {
            needConn = 2;
        } else {
            needConn = 1;
        }

        var conns = new ConnectableConnection[needConn];
        var __prefaceValue = new byte[8];
        ThreadLocalRandom.current().nextBytes(__prefaceValue);
        var prefaceValue = ByteArray.from(__prefaceValue);

        for (int i = 0; i < conns.length; ++i) {
            ConnectableConnection conn;
            try {
                conn = ConnectableConnection.create(toIPPort);
            } catch (IOException e) {
                Logger.error(LogType.SOCKET_ERROR, "failed to connect to " + toIPPort + " when received udp packet from " + remote, e);
                destroyConns(conns, i);
                return;
            }
            conn.setTimeout(5 * 1000);
            ByteArray prefaceTLV;
            {
                prefaceTLV = UOTUtils.buildHeader(UOTUtils.TYPE_CONN_ID, 8);
                prefaceTLV = prefaceTLV.concat(prefaceValue);
            }
            conn.getOutBuffer().storeBytesFrom(prefaceTLV);
            try {
                loop.addConnectableConnection(conn, null, new TCPHandler(pair, remote));
            } catch (IOException e) {
                Logger.error(LogType.SYS_ERROR, "failed to add connection " + conn + " to event loop when received udp packet from " + remote, e);
                conn.close();
                destroyConns(conns, i);
                return;
            }
            conns[i] = conn;
        }
        int idx = 0;
        if (pair.small == null) {
            pair.small = conns[idx++];
        }
        if (pair.large == null) {
            //noinspection UnusedAssignment
            pair.large = conns[idx++];
        }
    }

    private void destroyConns(ConnectableConnection[] conns, int endIndexExclusive) {
        for (int i = 0; i < endIndexExclusive; ++i) {
            conns[i].close();
        }
    }

    private class TCPHandler implements ConnectableConnectionHandler {
        private final ConnectionPair pair;
        final IPPort udpRemote;
        private final UOTHeaderParser parser = new UOTHeaderParser();

        private TCPHandler(ConnectionPair pair, IPPort udpRemote) {
            this.pair = pair;
            this.udpRemote = udpRemote;
        }

        @Override
        public void connected(ConnectableConnectionHandlerContext ctx) {
            if (!udp.isOpen()) {
                closed(ctx);
                return;
            }
            ctx.connection.setTimeout(60 * 1000);
            if (pair.small == ctx.connection) {
                Logger.access("new connection for small packet established " + udpRemote.formatToIPPortString() + " -> " + ctx.connection);
            } else if (pair.large == ctx.connection) {
                Logger.access("new connection for large packet established " + udpRemote.formatToIPPortString() + " -> " + ctx.connection);
            } else {
                Logger.access("new orphan connection established " + udpRemote.formatToIPPortString() + " -> " + ctx.connection);
            }
        }

        @Override
        public void readable(ConnectionHandlerContext ctx) {
            while (ctx.connection.getInBuffer().used() > 0) {
                doReadable(ctx);
            }
        }

        private void doReadable(ConnectionHandlerContext ctx) {
            if (!udp.isOpen()) {
                closed(ctx);
                return;
            }

            if (!parser.parse(ctx)) {
                return;
            }

            if (parser.type != UOTUtils.TYPE_PACKET) {
                parser.logInvalidExternalData("unknown type " + parser.type);
                return;
            }

            try {
                udp.send(parser.buf, udpRemote);
            } catch (IOException e) {
                Logger.error(LogType.SOCKET_ERROR, "failed to send udp packet to " + udpRemote, e);
            }
        }

        @Override
        public void writable(ConnectionHandlerContext ctx) {
            if (!udp.isOpen()) {
                closed(ctx);
            }
        }

        @Override
        public void exception(ConnectionHandlerContext ctx, IOException err) {
            Logger.error(LogType.SOCKET_ERROR, "exception occurred on " + ctx.connection, err);
            closed(ctx);
        }

        @Override
        public void remoteClosed(ConnectionHandlerContext ctx) {
            closed(ctx);
        }

        private final AtomicBoolean isClosed = new AtomicBoolean(false);

        @Override
        public void closed(ConnectionHandlerContext ctx) {
            if (!isClosed.compareAndSet(false, true)) {
                return;
            }

            ctx.connection.close();
            parser.clean();
            if (pair.small == ctx.connection) {
                pair.small = null;
                Logger.warn(LogType.ACCESS, "connection for small packet is removed: " + udpRemote.formatToIPPortString() + " -> " + ctx.connection);
            } else if (pair.large == ctx.connection) {
                pair.large = null;
                Logger.warn(LogType.ACCESS, "connection for large packet is removed: " + udpRemote.formatToIPPortString() + " -> " + ctx.connection);
            } else {
                Logger.warn(LogType.ACCESS, "orphan connection is removed: " + udpRemote.formatToIPPortString() + " -> " + ctx.connection);
            }
            if (pair.small == null && pair.large == null) {
                connections.remove(udpRemote);
            }
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            closed(ctx);
        }
    }
}
