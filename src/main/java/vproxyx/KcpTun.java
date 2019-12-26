package vproxyx;

import vproxy.component.proxy.ConnectorGen;
import vproxy.component.proxy.Proxy;
import vproxy.component.proxy.ProxyNetConfig;
import vproxy.connection.*;
import vproxy.dns.Resolver;
import vproxy.selector.SelectorEventLoop;
import vproxy.selector.wrap.h2streamed.H2StreamedClientFDs;
import vproxy.selector.wrap.h2streamed.H2StreamedServerFDs;
import vproxy.selector.wrap.kcp.KCPFDs;
import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.RingBuffer;
import vproxy.util.Utils;
import vproxyx.websocks.AlreadyConnectedConnector;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class KcpTun {
    private static final String HELP_STR = "" +
        "vproxy kcptun usage: mode {client|server} bind {port} target {host:port} [fast=1|2|3|4]" +
        "\n\t\tmode                     Run kcptun client or server" +
        "\n\t\tbind                     The listening port. TCP+localhost for client, UDP+any for server" +
        "\n\t\ttarget                   The target endpoint. KcpTun server for client, TCP endpoint for server" +
        "\n\t\tfast                     The retransmission speed" +
        "";

    public static void main0(String[] args) throws Exception {
        String modeStr = "";
        String bindStr = "";
        String targetStr = "";
        String fastStr = "";
        for (int i = 0; i < args.length; ++i) {
            String arg = args[i];
            String next = (args.length > i + 1) ? args[i + 1] : null;
            if (arg.equals("help") || arg.equals("-h") || arg.equals("--help") || arg.equals("-help")) {
                System.out.println(HELP_STR);
                return;
            }
            switch (arg) {
                case "mode":
                    if (next == null) {
                        throw new Exception("`mode` should be followed with `client` or `server`");
                    }
                    modeStr = next;
                    ++i;
                    break;
                case "bind":
                    if (next == null) {
                        throw new Exception("`bind` should be followed with the port to bind");
                    }
                    bindStr = next;
                    ++i;
                    break;
                case "target":
                    if (next == null) {
                        throw new Exception("`target` should be followed with the remote endpoint");
                    }
                    targetStr = next;
                    ++i;
                    break;
                case "fast":
                    if (next == null) {
                        throw new Exception("`fast` should be followed with the speed level");
                    }
                    fastStr = next;
                    ++i;
                    break;
                default:
                    throw new Exception("unknown parameter " + arg);
            }
        }
        modeStr = modeStr.trim();
        bindStr = bindStr.trim();
        targetStr = targetStr.trim();
        fastStr = fastStr.trim();
        if (modeStr.isEmpty()) {
            throw new Exception("`mode` should be set");
        }
        if (bindStr.isEmpty()) {
            throw new Exception("`bind` should be set");
        }
        if (targetStr.isEmpty()) {
            throw new Exception("`target` should be set");
        }
        if (fastStr.isEmpty()) {
            fastStr = "4";
        }

        boolean isServer;
        int port;
        InetSocketAddress target;
        KCPFDs kcpFDs;
        {
            if (modeStr.equals("client")) {
                isServer = false;
            } else if (modeStr.equals("server")) {
                isServer = true;
            } else {
                throw new Exception("invalid value for `mode`");
            }
            try {
                port = Integer.parseInt(bindStr);
            } catch (NumberFormatException e) {
                throw new Exception("invalid value for `bind`");
            }
            if (port < 1 || port > 65535) {
                throw new Exception("invalid value range for `bind`");
            }
            if (!targetStr.contains(":")) {
                throw new Exception("invalid value for `target`");
            }
            String hostPart = targetStr.substring(0, targetStr.lastIndexOf(":"));
            String portPart = targetStr.substring(targetStr.lastIndexOf(":") + 1);
            int targetPort;
            try {
                targetPort = Integer.parseInt(portPart);
            } catch (NumberFormatException e) {
                throw new Exception("invalid port value for `target`");
            }
            if (targetPort < 1 || targetPort > 65535) {
                throw new Exception("invalid port value range for `target`");
            }
            InetAddress l3addr = Resolver.getDefault().blockResolve(hostPart);
            target = new InetSocketAddress(l3addr, targetPort);
            int fast;
            try {
                fast = Integer.parseInt(fastStr);
            } catch (NumberFormatException e) {
                throw new Exception("invalid value for `fast`");
            }
            if (fast < 1 || fast > 4) {
                throw new Exception("invalid value range for `fast`");
            }
            if (fast == 1) {
                kcpFDs = KCPFDs.getFast1();
            } else if (fast == 2) {
                kcpFDs = KCPFDs.getFast2();
            } else if (fast == 3) {
                kcpFDs = KCPFDs.getFast3();
            } else {
                kcpFDs = KCPFDs.getDefault();
            }
        }

        System.out.println("mode:   " + (isServer ? "server" : "client"));
        System.out.println("bind:   " + port);
        System.out.println("target: " + target);
        System.out.println("fast:   " + fastStr);

        SelectorEventLoop sLoop = SelectorEventLoop.open();
        NetEventLoop loop = new NetEventLoop(sLoop);
        ServerSock sock;
        ConnectorGen connectorGen;
        if (isServer) {
            // listen on kcptun
            InetSocketAddress local = new InetSocketAddress(Utils.l3addr(new byte[]{0, 0, 0, 0}), port);
            ServerSock.checkBind(Protocol.UDP, local);
            sock = ServerSock.createUDP(local, sLoop, new H2StreamedServerFDs(kcpFDs, sLoop, local));
            // connect to remote server using raw tcp
            connectorGen = (v, hint) -> new Connector(target);
        } else {
            // listen on tcp
            InetSocketAddress local = new InetSocketAddress(Utils.l3addr(new byte[]{127, 0, 0, 1}), port);
            ServerSock.checkBind(local);
            sock = ServerSock.create(local);
            // connect to remote server using kcptun
            H2StreamedClientFDs fds = new H2StreamedClientFDs(kcpFDs, sLoop, target);
            connectorGen = (v, hint) -> {
                ConnectableConnection conn;
                try {
                    conn = ConnectableConnection.createUDP(target, new ConnectionOpts(),
                        RingBuffer.allocateDirect(16384), RingBuffer.allocateDirect(16384), sLoop, fds);
                } catch (IOException e) {
                    Logger.error(LogType.CONN_ERROR, "connecting to target failed", e);
                    return null;
                }
                return new AlreadyConnectedConnector(target, conn, loop);
            };
        }
        Proxy proxy = new Proxy(
            new ProxyNetConfig()
                .setAcceptLoop(loop)
                .setInBufferSize(16384)
                .setOutBufferSize(16384)
                .setHandleLoopProvider(v -> loop)
                .setServer(sock)
                .setConnGen(connectorGen),
            s -> {
                // do nothing, won't happen
                // when terminating, user should simply kill this process and won't close server
            });
        proxy.handle();
        sLoop.loop(n -> new Thread(n, "kcptun"));
    }
}
