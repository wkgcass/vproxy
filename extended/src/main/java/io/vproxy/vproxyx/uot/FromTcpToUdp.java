package io.vproxy.vproxyx.uot;

import io.vproxy.base.connection.*;
import io.vproxy.base.selector.Handler;
import io.vproxy.base.selector.HandlerContext;
import io.vproxy.base.util.*;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.base.util.direct.DirectMemoryUtils;
import io.vproxy.vfd.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class FromTcpToUdp {
    private final NetEventLoop loop;
    private final IPPort fromIPPort;
    private final IPPort toIPPort;
    private final ServerSock serverSock;
    private final Map<Long, UDPHandler> connIdToDatagramFD = new HashMap<>();

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
            connection.setTimeout(60 * 1000);
            try {
                loop.addConnection(connection, null, new TCPHandler());
            } catch (IOException e) {
                Logger.error(LogType.SYS_ERROR, "failed to add connection to event loop", e);
                connection.close();
            }
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
        final DatagramFD udp;
        private final long connId;
        private final ConnectionPair pair = new ConnectionPair();
        private ByteBufferEx _buf = DirectMemoryUtils.allocateDirectBuffer(65536);
        private final ByteBuffer buf = _buf.realBuffer();

        private UDPHandler(DatagramFD udp, long connId, Connection initialConnection) {
            this.udp = udp;
            this.connId = connId;
            this.pair.small = initialConnection;
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

            if (pair.small == null && pair.large == null) {
                assert Logger.lowLevelDebug("both small and large connections are null");
                return;
            }

            String connRef;
            Connection conn;
            if (len >= UOTUtils.LARGE_PACKET_LIMIT) {
                conn = pair.large;
                connRef = "large";
            } else if (len < UOTUtils.SMALL_PACKET_LIMIT) {
                conn = pair.small;
                connRef = "small";
            } else if (pair.small == null) {
                conn = pair.large;
                connRef = "large";
            } else if (pair.large == null) {
                conn = pair.small;
                connRef = "small";
            } else if (pair.large.getOutBuffer().free() > pair.small.getOutBuffer().free()) {
                conn = pair.large;
                connRef = "large";
            } else {
                conn = pair.small;
                connRef = "small";
            }

            if (conn == null) {
                Logger.warn(LogType.INVALID_STATE, "required connection for " + connRef + " packet is not established yet, udp: " +
                                                   getLocalAddress() + " -> " + getRemoteAddress());
                return;
            }

            if (conn.getOutBuffer().free() < UOTUtils.HEADER_LEN + len) {
                assert Logger.lowLevelDebug("tcp buffer free size is " + conn.getOutBuffer().free() +
                                            ", while receiving udp packet with length " + len + " from " + toIPPort.formatToIPPortString());
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
            try {
                ctx.getChannel().close();
            } catch (IOException ignore) {
            }
            if (_buf != null) {
                _buf.clean();
                _buf = null;
            }
            if (pair.small != null) {
                pair.small.close();
                pair.small = null;
            }
            if (pair.large != null) {
                pair.large.close();
                pair.large = null;
            }
            connIdToDatagramFD.remove(connId);
        }

        private IPPort localAddress;

        public String getLocalAddress() {
            if (localAddress != null) {
                return localAddress.formatToIPPortString();
            }
            try {
                localAddress = udp.getLocalAddress();
            } catch (IOException e) {
                return null;
            }
            return localAddress.formatToIPPortString();
        }

        private IPPort remoteAddress;

        public String getRemoteAddress() {
            if (remoteAddress != null) {
                return remoteAddress.formatToIPPortString();
            }
            try {
                remoteAddress = udp.getRemoteAddress();
            } catch (IOException e) {
                return null;
            }
            return remoteAddress.formatToIPPortString();
        }
    }

    private class TCPHandler implements ConnectableConnectionHandler {
        UDPHandler udp;
        private final UOTHeaderParser parser = new UOTHeaderParser();

        private TCPHandler() {
        }

        @Override
        public void connected(ConnectableConnectionHandlerContext ctx) {
            if (!udp.udp.isOpen()) {
                closed(ctx);
            }
        }

        @Override
        public void readable(ConnectionHandlerContext ctx) {
            while (ctx.connection.getInBuffer().used() > 0) {
                doReadable(ctx);
            }
        }

        private void doReadable(ConnectionHandlerContext ctx) {
            if (udp != null && !udp.udp.isOpen()) {
                closed(ctx);
                return;
            }

            if (!parser.parse(ctx)) {
                return;
            }

            if (parser.type == UOTUtils.TYPE_CONN_ID) {
                if (udp != null) {
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA,
                        "received CONN_ID message from " + ctx.connection.remote + ", but the message is already received");
                    return;
                }
                if (parser.len != 8) {
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA,
                        "received CONN_ID message from " + ctx.connection.remote + ", but message len is not 8");
                    return;
                }
                var connId = parser.buf.getLong();
                initUDPSock(connId, ctx.connection);
                return;
            }
            if (parser.type != UOTUtils.TYPE_PACKET) {
                parser.logInvalidExternalData("unknown type " + parser.type);
                return;
            }
            if (udp == null) {
                Logger.warn(LogType.INVALID_STATE,
                    "received PACKET message from " + ctx.connection.remote + ", but udp sock is not initialized yet");
                return;
            }

            try {
                udp.udp.write(parser.buf);
            } catch (IOException e) {
                Logger.error(LogType.SOCKET_ERROR, "failed to send udp packet to " + toIPPort, e);
            }
        }

        private void initUDPSock(long connId, Connection connection) {
            var udpHandler = connIdToDatagramFD.get(connId);
            if (udpHandler != null) {
                udp = udpHandler;
                if (udpHandler.pair.small == null) {
                    udpHandler.pair.small = connection;
                    Logger.access("received connection for small packet: " + connection +
                                  " -> " + udp.getLocalAddress() + " -> " + udp.getRemoteAddress());
                } else if (udpHandler.pair.large == null) {
                    udpHandler.pair.large = connection;
                    Logger.access("received connection for large packet: " + connection +
                                  " -> " + udp.getLocalAddress() + " -> " + udp.getRemoteAddress());
                } else {
                    Logger.access("received orphan connection: " + connection +
                                  " -> " + udp.getLocalAddress() + " -> " + udp.getRemoteAddress());
                }
                return;
            }
            DatagramFD udp = null;
            try {
                udp = FDProvider.get().getProvided().openDatagramFD();
                udp.configureBlocking(false);
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
            udpHandler = new UDPHandler(udp, connId, connection);
            try {
                loop.getSelectorEventLoop().add(udp, EventSet.read(), null, udpHandler);
            } catch (IOException e) {
                Logger.error(LogType.SYS_ERROR, "failed to add udp sock to event loop", e);
                connection.close();
                try {
                    udp.close();
                } catch (IOException ignore) {
                }
                return;
            }
            this.udp = udpHandler;
            Logger.access("received connection for small packet from " + connection.remote +
                          " -> " + udpHandler.getLocalAddress() + " -> " + udpHandler.getRemoteAddress());
            connIdToDatagramFD.put(connId, udpHandler);
        }

        @Override
        public void writable(ConnectionHandlerContext ctx) {
            if (!udp.udp.isOpen()) {
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
            if (udp != null) {
                if (udp.pair.small == ctx.connection) {
                    udp.pair.small = null;
                    Logger.warn(LogType.ACCESS, "connection for small packet is removed: " + ctx.connection +
                                                " -> " + udp.getLocalAddress() + " -> " + udp.getRemoteAddress());
                } else if (udp.pair.large == ctx.connection) {
                    udp.pair.large = null;
                    Logger.warn(LogType.ACCESS, "connection for large packet is removed: " + ctx.connection +
                                                " -> " + udp.getLocalAddress() + " -> " + udp.getRemoteAddress());
                } else {
                    Logger.warn(LogType.ACCESS, "orphan connection is removed: " + ctx.connection +
                                                " -> " + udp.getLocalAddress() + " -> " + udp.getRemoteAddress());
                }
                if (udp.pair.small == null && udp.pair.large == null) {
                    loop.getSelectorEventLoop().remove(udp.udp);
                }
            }
            parser.clean();
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            closed(ctx);
        }
    }
}
