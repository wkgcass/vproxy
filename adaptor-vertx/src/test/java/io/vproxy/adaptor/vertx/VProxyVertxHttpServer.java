package io.vproxy.adaptor.vertx;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class VProxyVertxHttpServer {
    public static void main(String[] args) {
        VProxyLogDelegateFactory.register();

        var vertx = Vertx.vertx(new VertxOptions()
            .setPreferNativeTransport(true) // this is essential
            .setEventLoopPoolSize(4)
        );
        vertx.createHttpServer()
            .requestHandler(req -> req.bodyHandler(body -> {
//                Logger.access("received req: " + req.method() + " " + req.uri() + " " + req.version()
//                    + "\n" + req.headers()
//                    + "\n" + body);

                req.response().send("Hello World\r\n");
            }))
            .listen(8080, "127.0.0.1");
    }
}
