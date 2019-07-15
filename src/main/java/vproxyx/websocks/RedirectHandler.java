package vproxyx.websocks;

import vproxy.http.HttpContext;
import vproxy.http.HttpProtocolHandler;
import vproxy.processor.http.entity.Header;
import vproxy.processor.http.entity.Response;
import vproxy.protocol.ProtocolHandlerContext;
import vproxy.util.ByteArray;

import java.util.Date;
import java.util.LinkedList;

public class RedirectHandler extends HttpProtocolHandler {
    private byte[] resp;

    public RedirectHandler(String protocol, String domain, int port) {
        super(false);

        Response resp = new Response();
        resp.version = "HTTP/1.1";
        resp.statusCode = 301;
        resp.reason = "Moved Permanently";
        resp.headers = new LinkedList<>();
        String location;
        {
            if ((protocol.equals("https") && port == 443) || (protocol.equals("http") && port == 80)) {
                location = protocol + "://" + domain;
            } else {
                location = protocol + "://" + domain + ":" + port;
            }
        }

        ByteArray body = ByteArray.from(("" +
            "<html>\r\n" +
            "<head><title>301 Moved Permanently</title></head>\r\n" +
            "<body bgcolor=\"white\">\r\n" +
            "<center><h1>301 Moved Permanently</h1></center>\r\n" +
            "<hr><center>nginx/1.14.2</center>\r\n" +
            "</body>\r\n" +
            "</html>\r\n").getBytes());

        resp.headers.add(new Header("Server", "nginx/1.14.2"));
        resp.headers.add(new Header("Date", new Date().toString()));
        resp.headers.add(new Header("Content-Type", "text/html"));
        resp.headers.add(new Header("Content-Length", "" + body.length()));
        resp.headers.add(new Header("Connection", "keep-alive"));
        resp.headers.add(new Header("Location", location));
        resp.body = body;
        this.resp = resp.toByteArray().toJavaArray();
    }

    @Override
    protected void request(ProtocolHandlerContext<HttpContext> ctx) {
        ctx.write(resp);
    }
}
