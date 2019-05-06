package net.cassite.vproxy.poc;

import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import net.cassite.vproxy.component.proxy.*;
import net.cassite.vproxy.connection.BindServer;
import net.cassite.vproxy.connection.Connection;
import net.cassite.vproxy.connection.Connector;
import net.cassite.vproxy.connection.NetEventLoop;
import net.cassite.vproxy.processor.http2.Http2Processor;
import net.cassite.vproxy.selector.SelectorEventLoop;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class Http2Proxy {
    @SuppressWarnings("deprecation")
    public static void main(String[] args) throws Exception {
        Vertx vertx = Vertx.vertx();
        vertx.createHttpServer().requestHandler(req -> {
            System.out.println("server get request " + req.method() + " " + req.uri());
            req.response().push(HttpMethod.POST, "/push-promise", r -> {
                if (r.succeeded()) {
                    System.out.println("server send push promise frame succeeded");
                    r.result().end("push-promise");
                } else {
                    System.err.println("push promise failed");
                    r.cause().printStackTrace();
                }
            });
            req.response().end(req.uri().substring(1));
        }).listen(17890);
        HttpClient client = vertx.createHttpClient(
            new HttpClientOptions()
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setHttp2ClearTextUpgrade(false));

        NetEventLoop el = new NetEventLoop(SelectorEventLoop.open());
        InetSocketAddress backend = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 17890);
        InetSocketAddress frontend = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 7890);
        BindServer svr = BindServer.create(frontend);
        Proxy proxy = new Proxy(new ProxyNetConfig()
            .setInBufferSize(8)
            .setOutBufferSize(4) // make them small to see whether the lib will work when payload is large
            .setAcceptLoop(el)
            .setConnGen(new ConnectorGen() {
                @Override
                public Type type() {
                    return Type.processor;
                }

                @Override
                public Connector genConnector(Connection accepted) {
                    return new Connector(backend);
                }

                @Override
                public Processor processor() {
                    return new Http2Processor();
                }
            })
            .setHandleLoopProvider(() -> el)
            .setServer(svr),
            server -> {
            });
        proxy.handle();

        el.getSelectorEventLoop().loop(Thread::new);

        int[] steps = {0};

        System.out.println("send http2 request");
        HttpClientRequest req = client.get(7890, "127.0.0.1", "/a");
        req.handler(resp -> resp.bodyHandler(buf -> {
            System.out.println("client get response: " + buf);
            ++steps[0];
        }));
        req.pushHandler(pushreq -> {
            System.out.println("client get push promise: " + pushreq.method() + " " + pushreq.uri());
            pushreq.handler(resp -> resp.bodyHandler(buf -> {
                System.out.println("client get push promise data: " + buf);
                ++steps[0];
            }));
        });
        req.end();

        while (steps[0] != 2) {
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
