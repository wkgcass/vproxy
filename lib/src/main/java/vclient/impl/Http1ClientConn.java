package vclient.impl;

import vclient.*;
import vlibbase.ConnRefPool;
import vlibbase.ConnectionAware;
import vproxybase.connection.*;
import vproxybase.http.HttpRespParser;
import vproxybase.processor.http1.entity.Header;
import vproxybase.processor.http1.entity.Request;
import vproxybase.processor.http1.entity.Response;
import vproxybase.util.ByteArray;
import vproxybase.util.Callback;
import vproxybase.util.Logger;
import vproxybase.util.Version;
import vproxybase.util.nio.ByteArrayChannel;
import vserver.HttpMethod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class Http1ClientConn implements HttpClientConn {
    private final Connection connection;
    private final NetEventLoop loop;
    private final ConnRefPool holdingClientPool;
    private final HttpClient.Options opts;

    private boolean validRef = true;
    private boolean transferring = false;
    private boolean tobeRemovedFromLoop = false;

    public Http1ClientConn(Connection connection, NetEventLoop loop, ConnRefPool holdingClientPool, HttpClient.Options opts) {
        this.connection = connection;
        this.loop = loop;
        this.holdingClientPool = holdingClientPool;
        this.opts = opts;
    }

    private void preCheck() {
        if (!validRef) {
            throw new IllegalStateException("the http1client conn is not valid");
        }
        if (transferring) {
            throw new IllegalStateException("the http1client conn is transferring");
        }
        if (connection.isClosed()) {
            throw new IllegalStateException("the connection is already closed");
        }
    }

    @Override
    public HttpRequest request(HttpMethod method, String uri) {
        preCheck();

        // it's used, so set to invalid
        // it will be reset to true when request is finished
        validRef = false;

        return new HttpRequest() {
            private final Request request = new Request();
            private final Map<String, String> headers = new LinkedHashMap<>();
            private boolean addUserAgent = true;
            private boolean addHost;

            {
                request.method = method.toString();
                request.uri = uri;
                request.version = "HTTP/1.1";
                addHost = opts.host != null;
            }

            @Override
            public HttpRequest header(String key, String value) {
                key = key.toLowerCase().trim();
                headers.put(key, value);
                if (key.equals("user-agent")) {
                    addUserAgent = false;
                }
                if (key.equals("host")) {
                    addHost = false;
                }
                return this;
            }

            @Override
            public void send(ByteArray body, ResponseHandler handler) {
                request.headers = new ArrayList<>(headers.size() + 3);
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    request.headers.add(new Header(entry.getKey(), entry.getValue()));
                }
                if (addHost) {
                    request.headers.add(new Header("host", opts.host));
                }
                if (addUserAgent) {
                    request.headers.add(new Header("user-agent", "vproxy/" + Version.VERSION));
                }
                if (body != null) {
                    request.headers.add(new Header("content-length", Integer.toString(body.length())));
                    request.body = body;
                }
                assert Logger.lowLevelDebug("http client sending request to " + connection.remote + " with " + request.toString());

                ByteArray array = request.toByteArray();
                ByteArrayChannel chnl = ByteArrayChannel.fromFull(array);

                System.out.println(connection.getEventLoop());
                try {
                    loop.addConnectableConnection(
                        (ConnectableConnection) connection,
                        null,
                        new ConnectableConnectionHandler() {
                            private final HttpRespParser parser = new HttpRespParser(true);
                            private final Callback<HttpResponse, IOException> cb = new Callback<>() {
                                @Override
                                protected void onSucceeded(HttpResponse value) {
                                    assert Logger.lowLevelDebug("http request succeeded with: " + value);
                                    handler.accept(null, value);
                                }

                                @Override
                                protected void onFailed(IOException err) {
                                    assert Logger.lowLevelDebug("http request failed with err: " + err);
                                    handler.accept(err, null);
                                }
                            };

                            @Override
                            public void connected(ConnectableConnectionHandlerContext ctx) {
                                connection.getOutBuffer().storeBytesFrom(chnl);
                            }

                            private void write(ConnectionHandlerContext ctx) {
                                ctx.connection.getOutBuffer().storeBytesFrom(chnl);
                            }

                            @Override
                            public void readable(ConnectionHandlerContext ctx) {
                                int res = parser.feed(ctx.connection.getInBuffer());
                                if (res == -1) {
                                    String msg = parser.getErrorMessage();
                                    if (msg == null) {
                                        // want more data
                                        return;
                                    }
                                    // error, close connection
                                    ctx.connection.close();
                                    cb.failed(new IOException("external data is not HTTP/1.x format"));
                                    return;
                                }
                                Response resp = parser.getResult();

                                // done
                                validRef = true;
                                if (opts.autoRelease) {
                                    assert Logger.lowLevelDebug("autoRelease is set to true, releasing the http1client conn");
                                    close();
                                }

                                cb.succeeded(new HttpResponseImpl(Http1ClientConn.this, resp));
                            }

                            @Override
                            public void writable(ConnectionHandlerContext ctx) {
                                write(ctx);
                            }

                            @Override
                            public void exception(ConnectionHandlerContext ctx, IOException err) {
                                cb.failed(err);
                                ctx.connection.close(true);
                            }

                            @Override
                            public void remoteClosed(ConnectionHandlerContext ctx) {
                                if (!cb.isCalled()) {
                                    cb.failed(new IOException("connection remote endpoint closed before receiving the response"));
                                }
                                ctx.connection.close();
                            }

                            @Override
                            public void closed(ConnectionHandlerContext ctx) {
                                if (!cb.isCalled()) {
                                    cb.failed(new IOException("connection closed before receiving the response"));
                                }
                            }

                            @Override
                            public void removed(ConnectionHandlerContext ctx) {
                                if (tobeRemovedFromLoop) {
                                    return;
                                }
                                ctx.connection.close();
                                if (!cb.isCalled()) {
                                    cb.failed(new IOException("removed from event loop"));
                                }
                            }
                        }
                    );
                } catch (IOException e) {
                    assert Logger.lowLevelDebug("http client failed to send request to " + connection.remote + ", " + e);
                    handler.accept(e, null);
                }
            }
        };
    }

    @Override
    public boolean isValidRef() {
        return validRef;
    }

    @Override
    public boolean isTransferring() {
        return transferring;
    }

    @Override
    public <T> T transferTo(ConnectionAware<T> client) throws IOException {
        preCheck();

        transferring = true;
        tobeRemovedFromLoop = true;
        loop.removeConnection(connection);
        var ret = client.receiveTransferredConnection0(this);
        transferring = false;
        validRef = false;

        return ret;
    }

    @Override
    public Connection raw() {
        return connection;
    }

    @Override
    public void close() {
        preCheck();

        if (holdingClientPool == null || holdingClientPool.isClosed()) {
            assert Logger.lowLevelDebug("no holding client, close the connection");
            var foo = tobeRemovedFromLoop;
            tobeRemovedFromLoop = true;
            connection.close();
            tobeRemovedFromLoop = foo;
        } else {
            assert Logger.lowLevelDebug("holding client exists, the connection will be stored into the pool");
            try {
                transferTo(holdingClientPool);
            } catch (IOException e) {
                Logger.shouldNotHappen("transferring http1client connection to holding pool failed", e);
                throw new RuntimeException(e);
            }
        }
    }
}
