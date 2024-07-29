package io.vproxy.vproxyx.uot;

import io.vproxy.base.connection.*;
import io.vproxy.base.selector.Handler;
import io.vproxy.base.selector.HandlerContext;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.ByteBufferEx;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.direct.DirectMemoryUtils;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.vfd.DatagramFD;
import io.vproxy.vfd.EventSet;
import io.vproxy.vfd.FDProvider;
import io.vproxy.vfd.IPPort;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class FromUdpToTcp {
    private final NetEventLoop loop;
    private final IPPort fromIPPort;
    private final IPPort toIPPort;
    private final DatagramFD udp;

    // remote udp endpoint -> to server connection
    private final Map<IPPort, ConnectableConnection> connections = new HashMap<>();

    public FromUdpToTcp(NetEventLoop loop, IPPort fromIPPort, IPPort toIPPort) throws IOException {
        this.loop = loop;
        this.fromIPPort = fromIPPort;
        this.toIPPort = toIPPort;

        udp = FDProvider.get().getProvided().openDatagramFD();
    }

    public void start() throws IOException {
        udp.bind(fromIPPort);
        loop.getSelectorEventLoop().add(udp, EventSet.read(), null, new UDPHandler());
    }

    private class UDPHandler implements Handler<DatagramFD> {
        private final ByteBufferEx _buf = DirectMemoryUtils.allocateDirectBuffer(65536);
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

            var conn = connections.get(remote);
            if (conn == null) {
                try {
                    conn = ConnectableConnection.create(toIPPort);
                } catch (IOException e) {
                    Logger.error(LogType.SOCKET_ERROR, "failed to connect to " + toIPPort + " when received udp packet from " + remote, e);
                    return;
                }
                connections.put(remote, conn);
                conn.setTimeout(60 * 1000);
                try {
                    loop.addConnectableConnection(conn, null, new TCPHandler(remote));
                } catch (IOException e) {
                    Logger.error(LogType.SYS_ERROR, "failed to add connection " + conn + " to event loop when received udp packet from " + remote, e);
                    conn.close();
                    return;
                }
            }
            if (conn.getOutBuffer().free() < TLVConsts.TL_LEN + len) {
                Logger.warn(LogType.ALERT, "tcp buffer free size is " + conn.getOutBuffer().free() +
                                           ", while receiving udp packet with length " + len + " from " + remote.formatToIPPortString());
                return;
            }

            var tl = ByteArray.allocate(TLVConsts.TL_LEN);
            tl.set(0, (byte) TLVConsts.TYPE_PACKET);
            tl.int16(1, len);
            var chnl = ByteArrayChannel.fromFull(tl);

            conn.getOutBuffer().storeBytesFrom(chnl);
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
            _buf.clean();
        }
    }

    private class TCPHandler implements ConnectableConnectionHandler {
        final IPPort udpRemote;
        private final TLVParser parser = new TLVParser();

        private TCPHandler(IPPort udpRemote) {
            this.udpRemote = udpRemote;
        }

        @Override
        public void connected(ConnectableConnectionHandlerContext ctx) {
            if (!udp.isOpen()) {
                closed(ctx);
                return;
            }
            Logger.access("new connection established, initiated by udp stream from " + udpRemote.formatToIPPortString());
        }

        @Override
        public void readable(ConnectionHandlerContext ctx) {
            if (!udp.isOpen()) {
                closed(ctx);
                return;
            }

            if (!parser.parse(ctx)) {
                return;
            }

            if (parser.type != TLVConsts.TYPE_PACKET) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "unknown type " + parser.type);
                return;
            }

            parser.buf.position(0);
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

        @Override
        public void closed(ConnectionHandlerContext ctx) {
            ctx.connection.close();
            connections.remove(udpRemote);
            parser.clean();
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            closed(ctx);
        }
    }
}
