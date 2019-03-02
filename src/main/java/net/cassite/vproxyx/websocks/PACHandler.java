package net.cassite.vproxyx.websocks;

import net.cassite.vproxy.http.HttpContext;
import net.cassite.vproxy.http.HttpProtocolHandler;
import net.cassite.vproxy.http.HttpReq;
import net.cassite.vproxy.protocol.ProtocolHandlerContext;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.Utils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class PACHandler extends HttpProtocolHandler {
    private static final String prefix = "function FindProxyForURL(url,host){return 'SOCKS ";
    private static final String suffix = "';}";

    private final String host;
    private final int port;

    public PACHandler(String host, int port) {
        super(false);
        this.host = host;
        this.port = port;
    }

    private String getIp() {
        if (!host.equals("*"))
            return host;

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
        String resp = prefix + getIp() + ":" + port + suffix;
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
