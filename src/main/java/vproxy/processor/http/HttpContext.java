package vproxy.processor.http;

import vproxy.processor.OOContext;
import vproxy.util.Utils;

import java.net.InetSocketAddress;

public class HttpContext extends OOContext<HttpSubContext> {
    final String clientAddress;

    int currentBackend = -1;

    public HttpContext(InetSocketAddress clientSock) {
        clientAddress = clientSock == null ? null : Utils.ipStr(clientSock.getAddress().getAddress());
    }

    @Override
    public int connection(HttpSubContext front) {
        if (front.isIdle()) {
            int foo = currentBackend;
            currentBackend = -1;
            return foo;
        }
        return currentBackend;
    }

    @Override
    public void chosen(HttpSubContext front, HttpSubContext subCtx) {
        currentBackend = subCtx.connId;
    }
}
