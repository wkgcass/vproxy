package io.vproxy.vproxyx.websocks;

import io.vproxy.base.connection.Connection;
import io.vproxy.base.http.HttpContext;
import io.vproxy.base.http.HttpProtocolHandler;
import io.vproxy.base.processor.http1.entity.Header;
import io.vproxy.base.processor.http1.entity.Request;
import io.vproxy.base.protocol.ProtocolHandlerContext;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.vfd.IP;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
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

    private final int socks5Port;
    private final int httpConnectPort;

    public PACHandler(int socksPort, int httpConnectPort) {
        super(false);
        this.socks5Port = socksPort;
        this.httpConnectPort = httpConnectPort;
    }

    private String getIp(ProtocolHandlerContext<HttpContext> ctx) {
        // try to get address from Host header
        List<Header> headers = ctx.data.result.headers;
        if (headers != null) {
            for (Header h : headers) {
                if (h.key.equalsIgnoreCase("host")) {
                    String v = h.value.trim();
                    if (v.isEmpty()) {
                        continue;
                    }
                    if (IP.isIpLiteral(v)) {
                        return v;
                    }
                    if (v.contains(":")) {
                        v = v.substring(0, v.lastIndexOf(':'));
                    }
                    return v;
                }
            }
        }
        // try to get local address from connection
        Connection conn = ctx.connection;
        try {
            return (conn.channel.getLocalAddress()).getAddress().formatToIPString();
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
            var addresses = nic.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                String strAddr = IP.ipStr(addr.getAddress());
                if (strAddr.equals("127.0.0.1") || strAddr.equals("::1")) {
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
        Request req = ctx.data.result;
        if (!req.method.equals("GET")) {
            sendError(ctx, "invalid method for retrieving pac");
            return;
        }
        if (ctx.inBuffer.used() != 0) {
            sendError(ctx, "this request cannot carry a body");
            return;
        }
        assert Logger.lowLevelDebug("got valid req, let's write response back");
        String ip = getIp(ctx);
        String socks5 = "";
        if (socks5Port != 0) {
            socks5 = "SOCKS5 " + ip + ":" + socks5Port;
        }
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
