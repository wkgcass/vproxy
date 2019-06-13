package net.cassite.vproxyx.websocks;

import net.cassite.vproxy.connection.Connection;
import net.cassite.vproxy.http.HttpContext;
import net.cassite.vproxy.http.HttpProtocolHandler;
import net.cassite.vproxy.http.HttpReq;
import net.cassite.vproxy.protocol.ProtocolHandlerContext;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.Utils;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.stream.Collectors;

public class PACHandler extends HttpProtocolHandler {
    private static final String template = Arrays.stream(("" +
        "  function FindProxyForURL(url, host) {" +
        "\n    if (url && url.indexOf('http://') === 0) {" +
        "\n        return '${SOCKS5};DIRECT';" +
        "\n    }" +
        "\n    return '${SOCKS5};${PROXY}';" +
        "\n}"
    ).split("\n")).map(String::trim).collect(Collectors.joining());

    private final String host;
    private final int port;
    private final int httpConnectPort;

    public PACHandler(String host, int socksPort, int httpConnectPort) {
        super(false);
        this.host = host;
        this.port = socksPort;
        this.httpConnectPort = httpConnectPort;
    }

    private String getIp(Connection conn) {
        if (!host.equals("*"))
            return host;

        // try to get local address from connection
        try {
            return Utils.ipStr(((InetSocketAddress) conn.channel.getLocalAddress()).getAddress().getAddress());
        } catch (IOException ignore) {
        }

        // try to get an address from nics
        Enumeration<NetworkInterface> nics;
        try {
            nics = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            Logger.error(LogType.UNEXPECTED, "get nics failed", e);
            return "127.0.0.1";
        }
        String lastSelectedV6 = null;
        while (nics.hasMoreElements()) {
            boolean isLocal = false;
            String selectedV4 = null;
            String selectedV6 = null;

            NetworkInterface nic = nics.nextElement();
            Enumeration<InetAddress> addresses = nic.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                String strAddr = Utils.ipStr(addr.getAddress());
                if (strAddr.equals("127.0.0.1") || strAddr.equals("0000:0000:0000:0000:0000:0000:0000:0001")) {
                    isLocal = true;
                    break;
                } else if (addr instanceof Inet4Address) {
                    selectedV4 = strAddr;
                } else {
                    selectedV6 = strAddr;
                }
            }
            // if the nic bond a local address
            // then we do not use any addresses
            if (!isLocal) {
                if (selectedV4 != null)
                    return selectedV4; // we always try to use v4 first
                if (selectedV6 != null) {
                    lastSelectedV6 = selectedV6; // store the result, if no v4 address, then use it
                }
            }
        }
        if (lastSelectedV6 != null)
            return lastSelectedV6;
        assert Logger.lowLevelDebug("no other addresses than local address");
        return "127.0.0.1";
    }

    @Override
    protected void request(ProtocolHandlerContext<HttpContext> ctx) {
        HttpReq req = ctx.data.result;
        if (!req.method.toString().equals("GET")) {
            sendError(ctx, "invalid method for retrieving pac");
            return;
        }
        if (ctx.inBuffer.used() != 0) {
            sendError(ctx, "this request cannot carry a body");
            return;
        }
        assert Logger.lowLevelDebug("got valid req, let's write response back");
        String ip = getIp(ctx.connection);
        String socks5 = "SOCKS5 " + ip + ":" + port;
        String proxy = "";
        if (httpConnectPort != 0) {
            proxy = "PROXY " + ip + ":" + httpConnectPort;
        }
        String resp = template.replace("${SOCKS5}", socks5).replace("${PROXY}", proxy);
        Logger.alert("respond pac string to " + ctx.connection.remote + ":\n" + resp);
        assert Logger.lowLevelDebug("respond with " + resp);
        ctx.write(("" +
            "HTTP/1.1 200 OK\r\n" +
            "Connection: Close\r\n" +
            "Content-Length: " + resp.getBytes().length + "\r\n" +
            "\r\n" +
            resp
        ).getBytes());
    }
}
