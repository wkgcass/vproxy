package vproxy.processor.http1;

import vproxy.processor.Hint;
import vproxy.processor.OOContext;
import vproxy.util.Utils;

import java.net.InetSocketAddress;

public class HttpContext extends OOContext<HttpSubContext> {
    final String clientAddress;
    final String clientPort;

    int currentBackend = -1;

    public HttpContext(InetSocketAddress clientSock) {
        clientAddress = clientSock == null ? null : Utils.ipStr(clientSock.getAddress().getAddress());
        clientPort = clientSock == null ? null : "" + clientSock.getPort();
    }

    @Override
    public int connection(HttpSubContext front) {
        if (front.isIdle()) {
            // the state may turn to idle after calling feed()
            // the connection() will be called after calling feed()
            // so here we should return the last recorded backend id
            // then set the id to -1
            int foo = currentBackend;
            currentBackend = -1;
            return foo;
        }
        return currentBackend;
    }

    @Override
    public Hint connectionHint(HttpSubContext front) {
        // TODO
        return null;
    }

    @Override
    public void chosen(HttpSubContext front, HttpSubContext subCtx) {
        currentBackend = subCtx.connId;
    }
}
