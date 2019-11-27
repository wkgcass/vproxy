package vproxyx;

import vclient.HttpClient;
import vfd.SocketFD;
import vproxy.connection.*;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.*;
import vproxy.util.nio.ByteArrayChannel;
import vserver.HttpServer;
import vserver.RoutingHandler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class HelloWorld {
    public static void main0(@SuppressWarnings("unused") String[] args) throws Exception {
        SelectorEventLoop sLoop = SelectorEventLoop.open();
        NetEventLoop nLoop = new NetEventLoop(sLoop);

        RoutingHandler h = rctx -> rctx.response().end(
            "Welcome to vproxy.\r\n" +
                "Your request address is " + Utils.l4addrStr(rctx.getRemote()) + ".\r\n" +
                "Server address is " + Utils.l4addrStr(rctx.getLocal()) + ".\r\n"
        );
        HttpServer.create().get("/hello", h).listen(80);
        HttpServer.create().get("/hello", h).listenIPv6(80);
        Logger.alert("HTTP servers are listening on port 80");

        HttpClient client = HttpClient.to("127.0.0.1", 80);
        sLoop.delay(1_000, () -> {
            Logger.alert("HTTP client now starts ...");
            Logger.alert("Making request: GET /hello");
            client.get("/hello").send((err, resp) -> {
                if (err != null) {
                    Logger.error(LogType.ALERT, "requesting server got error", err);
                    return;
                }
                Logger.alert("Server responds:\r\n" + resp.bodyAsString());
                Logger.alert("TCP seems OK");
            });
        });

        InetSocketAddress listenAddress = new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), 80);
        InetSocketAddress connectAddress = new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 80);
        final int bufferSize = 1024;
        ServerSock sock = ServerSock.createUDP(listenAddress, sLoop);
        nLoop.addServer(sock, null, new ServerHandler() {
            @Override
            public void acceptFail(ServerHandlerContext ctx, IOException err) {
                Logger.error(LogType.ALERT, "Accept(" + sock + ") failed", err);
            }

            @Override
            public void connection(ServerHandlerContext ctx, Connection connection) {
                try {
                    nLoop.addConnection(connection, null, new ConnectionHandler() {
                        @Override
                        public void readable(ConnectionHandlerContext ctx) {
                            // ignore, the buffers are piped
                        }

                        @Override
                        public void writable(ConnectionHandlerContext ctx) {
                            // ignore, the buffers are piped
                        }

                        @Override
                        public void exception(ConnectionHandlerContext ctx, IOException err) {
                            Logger.error(LogType.ALERT, "Connection " + connection + " got exception ", err);
                            ctx.connection.close();
                        }

                        @Override
                        public void remoteClosed(ConnectionHandlerContext ctx) {
                            ctx.connection.close();
                        }

                        @Override
                        public void closed(ConnectionHandlerContext ctx) {
                            // ignore
                        }

                        @Override
                        public void removed(ConnectionHandlerContext ctx) {
                            // ignore
                        }
                    });
                } catch (IOException e) {
                    Logger.error(LogType.ALERT, "adding connection " + connection + " from " + sock + " to event loop failed", e);
                }
            }

            @Override
            public Tuple<RingBuffer, RingBuffer> getIOBuffers(SocketFD channel) {
                var buf = RingBuffer.allocateDirect(bufferSize);
                return new Tuple<>(buf, buf); // pipe input to output
            }

            @Override
            public void removed(ServerHandlerContext ctx) {
                Logger.alert("server sock " + sock + " removed from loop");
            }
        });
        Logger.alert("UDP server is listening on 80");

        sLoop.delay(2_000, () -> {
            Logger.alert("UDP client now starts ...");
            try {
                ConnectableConnection conn = ConnectableConnection.createUDP(connectAddress, new ConnectionOpts(), RingBuffer.allocateDirect(bufferSize), RingBuffer.allocateDirect(bufferSize));
                nLoop.addConnectableConnection(conn, null, new ConnectableConnectionHandler() {
                    private static final String message = "hello world";

                    @Override
                    public void connected(ConnectableConnectionHandlerContext ctx) {
                        // send data when connected
                        String str = message;
                        Logger.alert("UDP client sends a message to server: " + str);
                        ctx.connection.getOutBuffer().storeBytesFrom(ByteArrayChannel.fromFull(str.getBytes()));
                    }

                    @Override
                    public void readable(ConnectionHandlerContext ctx) {
                        ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(bufferSize);
                        int len = ctx.connection.getInBuffer().writeTo(chnl);
                        String str = new String(chnl.getBytes(), 0, len);
                        Logger.alert("UDP client receives a message from server: " + str);
                        if (str.equals(message)) {
                            Logger.alert("UDP seems OK");
                            ctx.connection.close();
                        } else {
                            Logger.error(LogType.ALERT, "received message is not complete");
                        }
                    }

                    @Override
                    public void writable(ConnectionHandlerContext ctx) {
                        // ignore
                    }

                    @Override
                    public void exception(ConnectionHandlerContext ctx, IOException err) {
                        Logger.error(LogType.ALERT, "Connection " + conn + " got exception ", err);
                    }

                    @Override
                    public void remoteClosed(ConnectionHandlerContext ctx) {
                        ctx.connection.close();
                    }

                    @Override
                    public void closed(ConnectionHandlerContext ctx) {
                        // ignore
                    }

                    @Override
                    public void removed(ConnectionHandlerContext ctx) {
                        // ignore
                    }
                });
            } catch (IOException e) {
                Logger.error(LogType.ALERT, "Initiating UDP Client failed", e);
            }
        });

        sLoop.loop(Thread::new);
    }
}
