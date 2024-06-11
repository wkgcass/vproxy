package io.vproxy.base.processor.http1;

import io.vproxy.base.processor.Hint;
import io.vproxy.base.processor.OOContext;
import io.vproxy.base.processor.Processor;
import io.vproxy.vfd.IPPort;

public class HttpContext extends OOContext<HttpSubContext> {
    final String clientAddress;
    final String clientPort;

    int currentBackend = -1;
    boolean upgradedConnection = false;

    HttpSubContext frontendContext;

    public HttpContext(IPPort clientSock) {
        clientAddress = clientSock == null ? null : clientSock.getAddress().formatToIPString();
        clientPort = clientSock == null ? null : "" + clientSock.getPort();
    }

    Processor.ConnectionTODO connection(int backendId) {
        Processor.ConnectionTODO connectionTODO = Processor.ConnectionTODO.create();
        connectionTODO.connId = backendId;
        if (backendId == -1) {
            connectionTODO.hint = connectionHint();
            connectionTODO.chosen = this::chosen;
        }
        return connectionTODO;
    }

    private Hint connectionHint() {
        String uri = frontendSubCtx.reqParser.getBuilder().uri.toString();
        String host = frontendSubCtx.reqParser.getBuilder().lastHostHeader;

        if (host == null) {
            return Hint.ofUri(uri);
        } else {
            return Hint.ofHostUri(host, uri);
        }
    }

    private void chosen(Processor.SubContext subCtx) {
        currentBackend = subCtx.connId;
    }
}
