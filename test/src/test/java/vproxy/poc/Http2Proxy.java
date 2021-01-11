package vproxy.poc;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import vfd.IP;
import vfd.IPPort;
import vproxy.component.proxy.ConnectorGen;
import vproxy.component.proxy.Proxy;
import vproxy.component.proxy.ProxyNetConfig;
import vproxybase.connection.Connection;
import vproxybase.connection.Connector;
import vproxybase.connection.NetEventLoop;
import vproxybase.connection.ServerSock;
import vproxybase.processor.Hint;
import vproxybase.processor.Processor;
import vproxybase.processor.ProcessorProvider;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.util.thread.VProxyThread;

import java.io.IOException;

public class Http2Proxy {
    @SuppressWarnings("deprecation")
    public static void main(String[] args) throws Exception {
        Vertx vertx = Vertx.vertx();
        Handler<HttpServerRequest> handler = req -> {
            final int thisPort = req.localAddress().port();
            System.out.println("server " + req.localAddress() +
                " get request " + req.method() + " " + req.uri() +
                " headers " + req.headers().entries());
            String reqFlag = req.uri().substring(1);

            // we send the second response before the first one to see what will happen
            vertx.setTimer(reqFlag.equals("first") ? 500 : 1, l -> {
                req.response().push(HttpMethod.POST, "/push-promise/" + reqFlag, r -> {
                    if (r.succeeded()) {
                        System.out.println("server send push promise frame succeeded");
                        r.result().end("push-promise-for-" + reqFlag + "@" + thisPort);
                    } else {
                        System.err.println("push promise failed");
                        r.cause().printStackTrace();
                    }
                });
                req.response().end("response-for-" + reqFlag + "@" + thisPort);
            });
        };
        vertx.createHttpServer().requestHandler(handler).listen(17890);
        vertx.createHttpServer().requestHandler(handler).listen(17891);
        HttpClient client = vertx.createHttpClient(
            new HttpClientOptions()
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setHttp2ClearTextUpgrade(false));
        client.connectionHandler(conn -> System.out.println("client opens a new connection " + conn.localAddress()));

        NetEventLoop el = new NetEventLoop(SelectorEventLoop.open());
        IPPort backend1 = new IPPort(IP.from("127.0.0.1"), 17890);
        IPPort backend2 = new IPPort(IP.from("127.0.0.1"), 17891);
        IPPort frontend = new IPPort(IP.from("127.0.0.1"), 7890);
        ServerSock svr = ServerSock.create(frontend);
        Proxy proxy = new Proxy(new ProxyNetConfig()
            .setInBufferSize(8)
            .setOutBufferSize(4) // make them small to see whether the lib will work when payload is large
            .setAcceptLoop(el)
            .setConnGen(new ConnectorGen() {
                @Override
                public Type type() {
                    return Type.processor;
                }

                private int count = 0;

                @Override
                public Connector genConnector(Connection accepted, Hint hint) {
                    int n = count++;
                    if (n % 2 == 0) {
                        return new Connector(backend1);
                    } else {
                        return new Connector(backend2);
                    }
                }

                @Override
                public Processor processor() {
                    return ProcessorProvider.getInstance().get("h2");
                }
            })
            .setHandleLoopProvider(ignore -> el)
            .setServer(svr),
            server -> {
            });
        proxy.handle();

        el.getSelectorEventLoop().loop(r -> VProxyThread.create(r, "proxy"));

        int[] steps = {0};

        {
            System.out.println("send http/2 request: first");
            HttpClientRequest req = client.get(7890, "127.0.0.1", "/first");
            req.handler(resp -> resp.bodyHandler(buf -> {
                System.out.println("client 1 get response: " + buf);
                ++steps[0];
            }));
            req.pushHandler(pushreq -> {
                System.out.println("client 1 get push promise: " + pushreq.method() + " " + pushreq.uri());
                pushreq.handler(resp -> resp.bodyHandler(buf -> {
                    System.out.println("client 1 get push promise data: " + buf);
                    ++steps[0];
                }));
            });
            req.end();
        }

        {
            System.out.println("send http/2 request: second");
            HttpClientRequest req = client.get(7890, "127.0.0.1", "/second");
            req.headers().add("X-Forwarded-For", "my-test");
            req.handler(resp -> resp.bodyHandler(buf -> {
                System.out.println("client 2 get response: " + buf);
                ++steps[0];
            }));
            req.pushHandler(pushreq -> {
                System.out.println("client 2 get push promise: " + pushreq.method() + " " + pushreq.uri());
                pushreq.handler(resp -> resp.bodyHandler(buf -> {
                    System.out.println("client 2 get push promise data: " + buf);
                    ++steps[0];
                }));
            });
            req.end();
        }

        while (steps[0] != 4) {
            Thread.sleep(1);
        }
        vertx.close(r -> {
            svr.close();
            try {
                el.getSelectorEventLoop().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
