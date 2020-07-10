package vproxyx.websocks;

import vproxybase.util.ByteArray;
import vproxybase.util.ClasspathResourceHolder;
import vserver.HttpServer;

import java.io.IOException;

public class AdminServer {
    private final HttpServer httpServer;
    private final ClasspathResourceHolder holder = new ClasspathResourceHolder("/vpws/agent/webroot");

    public AdminServer() {
        var http = HttpServer.create();
        this.httpServer = http;

        http.get("/", ctx -> ctx.response().status(302).header("Location", "/index.html").end());
        http.get("/*", ctx -> {
            String path = ctx.uri();
            ByteArray b = holder.get(path);
            if (b == null) {
                ctx.response().status(404).end("Page Not Found\r\n");
            } else {
                ctx.response().header("Content-Type", "text/html").end(b);
            }
        });
    }

    public void listen(int port) throws IOException {
        httpServer.listen(port, "127.0.0.1");
    }
}
