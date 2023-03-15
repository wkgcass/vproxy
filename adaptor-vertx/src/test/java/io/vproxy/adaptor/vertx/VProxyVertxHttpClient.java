package io.vproxy.adaptor.vertx;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;

public class VProxyVertxHttpClient {
    public static void main(String[] args) {
        VProxyLogDelegateFactory.register();

        var vertx = Vertx.vertx(new VertxOptions()
            .setPreferNativeTransport(true) // this is essential
            .setEventLoopPoolSize(1)
        );
        vertx.createHttpClient(new HttpClientOptions()
                .setKeepAlive(true)
                .setKeepAliveTimeout(60))
            .request(HttpMethod.GET, 8080, "127.0.0.1", "/test/")
            .compose(HttpClientRequest::send)
            .onSuccess(resp -> resp.body(body -> {
                Logger.alert(resp.version() + " " + resp.statusCode()
                    + "\n" + resp.headers()
                    + "\n" + body.result());
                vertx.close().onComplete(vv -> System.exit(0));
            }))
            .onFailure(t -> {
                Logger.error(LogType.ALERT, "failed to request", t);
                vertx.close().onComplete(vv -> System.exit(0));
            });
    }
}
