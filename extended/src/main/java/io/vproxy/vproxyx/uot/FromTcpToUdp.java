package io.vproxy.vproxyx.uot;

import io.vproxy.base.connection.*;
import io.vproxy.base.selector.Handler;
import io.vproxy.base.selector.HandlerContext;
import io.vproxy.base.util.*;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.base.util.direct.DirectMemoryUtils;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.vfd.*;

import java.io.IOException;
import java.nio.ByteBuffer;

public class FromTcpToUdp {
    private final NetEventLoop loop;
    private final IPPort fromIPPort;
    private final IPPort toIPPort;
    private final ServerSock serverSock;

    public FromTcpToUdp(NetEventLoop loop, IPPort fromIPPort, IPPort toIPPort) throws IOException {
        this.loop = loop;
        this.fromIPPort = fromIPPort;
        this.toIPPort = toIPPort;

        serverSock = ServerSock.create(fromIPPort);
    }

    public void start() throws IOException {
        loop.addServer(serverSock, null, new TcpServerHandler());
    }

    private class TcpServerHandler implements ServerHandler {
        @Override
        public void acceptFail(ServerHandlerContext ctx, IOException err) {
            Logger.error(LogType.SOCKET_ERROR, "failed to accept connection from " + ctx.server, err);
        }

        @Override
        public void connection(ServerHandlerContext ctx, Connection connection) {
            DatagramFD udp = null;
            try {
                udp = FDProvider.get().getProvided().openDatagramFD();
                udp.connect(toIPPort);
            } catch (IOException e) {
                Logger.error(LogType.SOCKET_ERROR, "failed to open udp sock to " + toIPPort, e);
                if (udp != null) {
                    try {
                        udp.close();
                    } catch (IOException ignore) {
                    }
                }
                connection.close();
                return;
            }
            try {
                loop.getSelectorEventLoop().add(udp, EventSet.read(), null, new UDPHandler(connection));
            } catch (IOException e) {
                Logger.error(LogType.SYS_ERROR, "failed to add udp sock to event loop", e);
                connection.close();
                try {
                    udp.close();
                } catch (IOException ignore) {
                }
                return;
            }
            connection.setTimeout(60 * 1000);
            try {
                loop.addConnection(connection, null, new TCPHandler(udp));
            } catch (IOException e) {
                Logger.error(LogType.SYS_ERROR, "failed to add connection to event loop", e);
                connection.close();
                try {
                    udp.close();
                } catch (IOException ignore) {
                }
                return;
            }
            Logger.access("received connection from " + connection.remote);
        }

        @Override
        public Tuple<RingBuffer, RingBuffer> getIOBuffers(SocketFD channel) {
            return new Tuple<>(RingBuffer.allocateDirect(16384), RingBuffer.allocateDirect(16384));
        }

        @Override
        public void removed(ServerHandlerContext ctx) {
            Logger.shouldNotHappen("server sock " + ctx.server + " is removed from event loop");
            serverSock.close();
        }
    }

    private class UDPHandler implements Handler<DatagramFD> {
        private final Connection conn;
        private final ByteBufferEx _buf = DirectMemoryUtils.allocateDirectBuffer(65536);
        private final ByteBuffer buf = _buf.realBuffer();

        private UDPHandler(Connection connection) {
            this.conn = connection;
        }

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
            if (conn.isClosed()) {
                loop.getSelectorEventLoop().remove(ctx.getChannel());
                return;
            }

            buf.limit(buf.capacity()).position(0);
            int len;
            try {
                len = ctx.getChannel().read(buf);
            } catch (IOException e) {
                Logger.error(LogType.SOCKET_ERROR, "failed to read udp packet from " + fromIPPort, e);
                loop.getSelectorEventLoop().remove(ctx.getChannel());
                return;
            }
            buf.flip();

            if (conn.getOutBuffer().free() < TLVConsts.TL_LEN + len) {
                Logger.warn(LogType.ALERT, "tcp buffer free size is " + conn.getOutBuffer().free() +
                                           ", while receiving udp packet with length " + len + " from " + toIPPort.formatToIPPortString());
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
            try {
                ctx.getChannel().close();
            } catch (IOException ignore) {
            }
            _buf.clean();
            conn.close();
        }
    }

    private class TCPHandler implements ConnectableConnectionHandler {
        final DatagramFD udp;
        private final TLVParser parser = new TLVParser();

        private TCPHandler(DatagramFD udp) {
            this.udp = udp;
        }

        @Override
        public void connected(ConnectableConnectionHandlerContext ctx) {
            if (!udp.isOpen()) {
                closed(ctx);
            }
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
                udp.write(parser.buf);
            } catch (IOException e) {
                Logger.error(LogType.SOCKET_ERROR, "failed to send udp packet to " + toIPPort, e);
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
            try {
                udp.close();
            } catch (IOException ignore) {
            }
            parser.clean();
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            closed(ctx);
        }
    }
}
