package vclient.impl;

import vclient.HttpClient;
import vclient.HttpRequest;
import vclient.HttpResponse;
import vclient.ResponseHandler;
import vproxy.app.Application;
import vproxy.connection.*;
import vproxy.http.HttpRespParser;
import vproxy.processor.http1.entity.Header;
import vproxy.processor.http1.entity.Request;
import vproxy.processor.http1.entity.Response;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.*;
import vserver.HttpMethod;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Http1ClientImpl implements HttpClient {
    private final InetSocketAddress remote;
    private final boolean noInputLoop;
    private NetEventLoop loop;
    private final int timeout;
    private boolean closed = false;

    public Http1ClientImpl(InetSocketAddress remote) {
        this(remote, null, 10_000);
    }

    public Http1ClientImpl(InetSocketAddress remote, NetEventLoop loop, int timeout) {
        this.remote = remote;
        this.loop = loop;
        this.timeout = timeout;
        noInputLoop = (loop == null);
    }

    private void initLoop() {
        if (loop != null) {
            return;
        }
        try {
            loop = new NetEventLoop(SelectorEventLoop.open());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        loop.getSelectorEventLoop().loop(Thread::new);
    }

    @Override
    public HttpRequest request(HttpMethod method, String uri) {
        return new HttpRequest() {
            private Request request = new Request();
            private Map<String, String> headers = new HashMap<>();
            private boolean addUserAgent = true;

            {
                request.method = method.toString();
                request.uri = uri;
                request.version = "HTTP/1.1";
            }

            @Override
            public HttpRequest header(String key, String value) {
                key = key.toLowerCase();
                headers.put(key, value);
                if (key.equals("user-agent")) {
                    addUserAgent = false;
                }
                return this;
            }

            @Override
            public void send(ByteArray body, ResponseHandler handler) {
                request.headers = new ArrayList<>(headers.size() + 2);
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    request.headers.add(new Header(entry.getKey(), entry.getValue()));
                }
                if (addUserAgent) {
                    request.headers.add(new Header("User-Agent", "vproxy/" + Application.VERSION));
                }
                if (body != null) {
                    request.headers.add(new Header("Content-Length", Integer.toString(body.length())));
                    request.body = body;
                }
                assert Logger.lowLevelDebug("http client sending request to " + remote + " with " + request.toString());

                ByteArray array = request.toByteArray();
                ByteArrayChannel chnl = ByteArrayChannel.fromFull(array);

                initLoop();

                try {
                    loop.addClientConnection(
                        ClientConnection.create(remote, new ConnectionOpts().setTimeout(timeout),
                            RingBuffer.allocate(1024), RingBuffer.allocate(1024)),
                        null,
                        new ClientConnectionHandler() {
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

                            private void write(ConnectionHandlerContext ctx) {
                                ctx.connection.getOutBuffer().storeBytesFrom(chnl);
                            }

                            @Override
                            public void connected(ClientConnectionHandlerContext ctx) {
                                // write data when connected
                                write(ctx);
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
                                cb.succeeded(new HttpResponseImpl(resp));
                                ctx.connection.close();
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
                                ctx.connection.close();
                                closed(ctx);
                            }

                            @Override
                            public void closed(ConnectionHandlerContext ctx) {
                                if (!cb.isCalled()) {
                                    cb.failed(new IOException("connection closed before receiving the response"));
                                }
                            }

                            @Override
                            public void removed(ConnectionHandlerContext ctx) {
                                ctx.connection.close();
                                if (!cb.isCalled()) {
                                    cb.failed(new IOException("removed from event loop"));
                                }
                            }
                        }
                    );
                } catch (IOException e) {
                    assert Logger.lowLevelDebug("http client failed to send request to " + remote + ", " + e);
                    handler.accept(e, null);
                }
            }
        };
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (noInputLoop) {
            // should close the input loop because it's created by the lib
            try {
                loop.getSelectorEventLoop().close();
            } catch (IOException e) {
                Logger.shouldNotHappen("got error when closing the event loop", e);
            }
        }
    }
}
