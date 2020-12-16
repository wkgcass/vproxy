package vclient.impl;

import vclient.*;
import vfd.IPPort;
import vlibbase.ConnRef;
import vlibbase.ConnRefPool;
import vlibbase.VProxyLibUtils;
import vlibbase.impl.ConnRefPoolImpl;
import vproxybase.util.*;
import vserver.HttpMethod;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class Http1ClientImpl extends AbstractClient implements HttpClient {
    private final StreamClient streamClient;
    private final ConnRefPool pool;
    private final Options opts;

    public Http1ClientImpl(IPPort remote, Options opts) {
        super(opts);
        this.opts = opts;

        getLoop();
        streamClient = new StreamClientImpl(remote, new StreamClient.Options().fill(opts).setAlpn(new String[]{"http/1.1"}).setClientContext(getClientContext()));
        if (opts.poolOptions == null || opts.poolOptions.maxCount == 0) {
            pool = null;
        } else {
            pool = new ConnRefPoolImpl(new ConnRefPool.Options(opts.poolOptions).setLoop(getLoop()));
        }
    }

    private class Http1ClientRequestImpl implements HttpRequest {
        private final HttpMethod method;
        private final String uri;
        private final Map<String, String> headers = new LinkedHashMap<>();

        private HttpClientConn conn;

        Http1ClientRequestImpl(HttpMethod method, String uri) {
            this.method = method;
            this.uri = uri;
        }

        @Override
        public HttpRequest header(String key, String value) {
            key = key.toLowerCase();
            headers.put(key, value);
            return this;
        }

        @Override
        public void send(ByteArray body, ResponseHandler handler) {
            Optional<ConnRef> pooledConnOptional = pool == null ? Optional.empty() : pool.get();
            if (pooledConnOptional.isPresent()) {
                HttpClientConn conn;
                try {
                    conn = pooledConnOptional.get().transferTo(Http1ClientImpl.this);
                } catch (IOException e) {
                    Logger.shouldNotHappen("transferring conn from pool " + pool + " to http1client " + Http1ClientImpl.this + " failed", e);
                    throw new RuntimeException(e);
                }
                this.conn = conn;
                doSend(body, handler);
                return;
            }
            streamClient.connect((err, conn) -> {
                if (err != null) {
                    handler.accept(err, null);
                    return;
                }
                HttpClientConn h1conn;
                try {
                    h1conn = conn.transferTo(Http1ClientImpl.this);
                } catch (IOException e) {
                    Logger.shouldNotHappen("transferring conn from stream client " + streamClient + " to http1client " + Http1ClientImpl.this + " failed", e);
                    throw new RuntimeException(e);
                }
                this.conn = h1conn;
                doSend(body, handler);
            });
        }

        private void doSend(ByteArray body, ResponseHandler handler) {
            var req = conn.request(method, uri);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                req.header(header.getKey(), header.getValue());
            }
            req.send(body, handler);
        }
    }

    @Override
    public HttpRequest request(HttpMethod method, String uri) {
        return new Http1ClientRequestImpl(method, uri);
    }

    @Override
    public HttpClientConn receiveTransferredConnection0(ConnRef conn) throws IOException {
        VProxyLibUtils.checkTransfer(this, conn);

        var raw = conn.raw();
        raw.setTimeout(opts.timeout);
        VProxyLibUtils.switchBuffers(raw, opts);
        return new Http1ClientConn(raw, getLoop(), pool, opts);
    }
}
