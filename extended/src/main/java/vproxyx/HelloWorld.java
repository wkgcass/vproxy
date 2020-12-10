package vproxyx;

import vclient.HttpClient;
import vfd.IP;
import vfd.IPPort;
import vfd.SocketFD;
import vfd.VFDConfig;
import vproxybase.Config;
import vproxybase.connection.*;
import vproxybase.dhcp.DHCPClientHelper;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.util.*;
import vproxybase.util.nio.ByteArrayChannel;
import vserver.HttpServer;

import java.io.IOException;
import java.util.Set;

public class HelloWorld {
    public static void main0(@SuppressWarnings("unused") String[] args) throws Exception {
        Logger.alert("You are using vproxy " + Version.VERSION);

        SelectorEventLoop sLoop = SelectorEventLoop.open();
        NetEventLoop nLoop = new NetEventLoop(sLoop);

        sLoop.loop(Thread::new);

        if (VFDConfig.useFStack) {
            Logger.warn(LogType.ALERT, "DHCP will not run when using FStack");
        } else if (!Config.dhcpGetDnsListEnabled) {
            Logger.alert("Feature 'dhcp to get dns list' NOT enabled.");
            Logger.alert("You may set -DhcpGetDnsListNics=all or eth0,eth1,... to enable the feature.");
        } else {
            Logger.alert("Retrieving dns servers using DHCP ...");
            BlockCallback<Set<IP>, IOException> cb = new BlockCallback<>();
            DHCPClientHelper.getDomainNameServers(sLoop, Config.dhcpGetDnsListNics, 1, cb);
            try {
                var ips = cb.block();
                Logger.alert("dhcp returns with dns servers: " + ips);
            } catch (IOException e) {
                Logger.warn(LogType.ALERT, "failed to retrieve dns servers from dhcp", e);
            }
        }

        final int listenPort = 8080;

        HttpServer.create()
            .get("/", rctx -> rctx.response().end(
                "vproxy " + Version.VERSION + "\r\n"
            ))
            .get("/hello", rctx -> rctx.response().end(
                "Welcome to vproxy " + Version.VERSION + ".\r\n" +
                    "Your request address is " + rctx.getRemote().formatToIPPortString() + ".\r\n" +
                    "Server address is " + rctx.getLocal().formatToIPPortString() + ".\r\n"
            ))
            .listen(listenPort);
        Logger.alert("HTTP server is listening on " + listenPort);

        if (VFDConfig.useFStack) {
            Logger.warn(LogType.ALERT, "F-Stack does not support 127.0.0.1 nor to request self ip address.");
            Logger.warn(LogType.ALERT, "You may run `curl $ip:" + listenPort + "/hello` to see if the TCP server is working.");
            Logger.warn(LogType.ALERT, "Or you may run -Deploy=Simple to start a simple loadbalancer to verify the TCP client functions.");
        } else {
            HttpClient client = HttpClient.to("127.0.0.1", listenPort);
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
        }

        IPPort listenAddress = new IPPort(IP.from(new byte[]{0, 0, 0, 0}), listenPort);
        IPPort connectAddress = new IPPort(IP.from(new byte[]{127, 0, 0, 1}), listenPort);
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
        Logger.alert("UDP server is listening on " + listenPort);

        if (VFDConfig.useFStack) {
            Logger.warn(LogType.ALERT, "You may run `nc -u $ip " + listenPort + "` to see if the UDP server is working. It's an echo server, it will respond with anything you input.");
        } else {
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
        }
    }
}
