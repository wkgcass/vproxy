package vproxy.base.processor.http1;

import vproxy.base.processor.Hint;
import vproxy.base.processor.OOContext;
import vproxy.base.processor.Processor;
import vproxy.base.util.ByteArray;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.vfd.IPPort;

public class HttpContext extends OOContext<HttpSubContext> {
    final String clientAddress;
    final String clientPort;

    int currentBackend = -1;
    boolean upgradedConnection = false;

    boolean frontendExpectingResponse = false;
    int frontendExpectingResponseFrom = -1; // backend connId

    public HttpContext(IPPort clientSock) {
        clientAddress = clientSock == null ? null : clientSock.getAddress().formatToIPString();
        clientPort = clientSock == null ? null : "" + clientSock.getPort();
    }

    Processor.ConnectionTODO connection() {
        int returnConnId;
        if (frontendSubCtx.isIdle()) {
            // the state may turn to idle after calling feed()
            // the connection() will be called after calling feed()
            // so here we should return the last recorded backend id
            // then set the id to -1
            int foo = currentBackend;
            currentBackend = -1;
            returnConnId = foo;
        } else {
            returnConnId = currentBackend;
        }

        if (frontendExpectingResponse && returnConnId != -1) {
            // the data is proxied to the specified backend
            // so response is expected to be from that backend
            frontendExpectingResponseFrom = returnConnId;
        }

        Processor.ConnectionTODO connectionTODO = Processor.ConnectionTODO.create();
        connectionTODO.connId = returnConnId;
        if (returnConnId == -1) {
            connectionTODO.hint = connectionHint();
            connectionTODO.chosen = this::chosen;
        }
        return connectionTODO;
    }

    private Hint connectionHint() {
        String uri = frontendSubCtx.theUri;
        String host = frontendSubCtx.theHostHeader;

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

    private void chosen(Processor.SubContext subCtx) {
        currentBackend = subCtx.connId;
        // backend chosen, so response is expected to be from that backend
        frontendExpectingResponseFrom = currentBackend;
    }

    void clearFrontendExpectingResponse(Processor.SubContext subCtx) {
        if (!frontendExpectingResponse) {
            Logger.error(LogType.IMPROPER_USE, "frontend expecting response is already false");
            return;
        }
        if (frontendExpectingResponseFrom != subCtx.connId) {
            Logger.error(LogType.IMPROPER_USE, "the expected response is from " + frontendExpectingResponseFrom + ", not " + subCtx.connId);
            return;
        }
        frontendExpectingResponseFrom = -1;
        frontendExpectingResponse = false;

        frontendSubCtx.delegate.resume();
        if (frontendSubCtx.storedBytesForProcessing != null) {
            try {
                frontendSubCtx.feed(ByteArray.allocate(0));
            } catch (Exception e) {
                assert Logger.lowLevelDebug("feed return error: " + e);
            }
        }
    }
}
