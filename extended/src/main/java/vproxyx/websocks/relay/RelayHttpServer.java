package vproxyx.websocks.relay;

import vfd.IP;
import vproxybase.component.elgroup.EventLoopGroup;
import vproxybase.util.ErrorPages;
import vserver.HttpServer;
import vserver.RoutingHandler;
import vserver.server.Http1ServerImpl;

import java.io.IOException;

public class RelayHttpServer {
    private RelayHttpServer() {
    }

    public static void launch(EventLoopGroup worker) throws IOException {
        HttpServer server = new Http1ServerImpl(worker.next());
        RoutingHandler handler = rctx -> {
            String host = rctx.header("host");
            if (host != null) {
                if (host.contains(":")) {
                    host = host.substring(0, host.indexOf(":"));
                }
            }
            if (IP.isIpLiteral(host)) {
                host = null;
            }
            if (host == null || host.isEmpty()) {
                String respBody = ErrorPages.build("VPROXY ERROR PAGE",
                    "Cannot handle the request",
                    "no `Host` header available, or `Host` header is ip");
                rctx.response().status(400).header("Connection", "Close").end(respBody);
                return;
            }
            String newUrl = "https://" + host + rctx.uri();
            rctx.response().status(302).header("Location", newUrl).header("Connection", "Close").end();
        };
        server.get("/*", handler);
        server.get("/", handler);
        server.listen(80);
    }
}
