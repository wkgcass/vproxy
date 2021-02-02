package vproxybase.processor.http1;

import vfd.IPPort;
import vproxybase.processor.Hint;
import vproxybase.processor.OOContext;

public class HttpContext extends OOContext<HttpSubContext> {
    final String clientAddress;
    final String clientPort;

    int currentBackend = -1;
    boolean upgradedConnection = false;

    public HttpContext(IPPort clientSock) {
        clientAddress = clientSock == null ? null : clientSock.getAddress().formatToIPString();
        clientPort = clientSock == null ? null : "" + clientSock.getPort();
    }

    @Override
    public int connection(HttpSubContext front) {
        if (!front.hostHeaderRetrieved) {
            return 0; // do not send data for now
        }
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
        String uri = front.theUri;
        String host = front.theHostHeader;

        if (host == null && uri == null) {
            return null;
        } else if (host == null) {
            // assert uri != null;
            return Hint.ofUri(uri);
        } else if (uri == null) {
            // assert host != null;
            return Hint.ofHost(host);
        } else {
            // assert host != null && uri != null;
            return Hint.ofHostUri(host, uri);
        }
    }

    @Override
    public void chosen(HttpSubContext front, HttpSubContext subCtx) {
        currentBackend = subCtx.connId;
    }
}
