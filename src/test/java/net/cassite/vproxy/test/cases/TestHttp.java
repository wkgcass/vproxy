package net.cassite.vproxy.test.cases;

import net.cassite.vproxy.connection.BindServer;
import net.cassite.vproxy.connection.NetEventLoop;
import net.cassite.vproxy.http.HttpContext;
import net.cassite.vproxy.http.HttpProtocolHandler;
import net.cassite.vproxy.protocol.ProtocolHandlerContext;
import net.cassite.vproxy.protocol.ProtocolServerConfig;
import net.cassite.vproxy.protocol.ProtocolServerHandler;
import net.cassite.vproxy.selector.SelectorEventLoop;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class TestHttp {
    private NetEventLoop loop;

    @Before
    public void setUp() throws Exception {
        loop = new NetEventLoop(SelectorEventLoop.open());
        loop.getSelectorEventLoop().loop(r -> new Thread(r, "TestHttpLoop"));
    }

    @After
    public void tearDown() throws Exception {
        loop.getSelectorEventLoop().close();
    }

    @Test
    public void simple() throws Exception {
        ProtocolServerHandler.apply(loop,
            BindServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 18080)),
            new ProtocolServerConfig().setInBufferSize(16384).setOutBufferSize(16384),
            new HttpProtocolHandler() {
                @Override
                protected void request(ProtocolHandlerContext<HttpContext> ctx) {
                    String result = ctx.data.result.toString();
                    System.out.println(result);
                    String respHtml = "<html><body>" + result.replaceAll("\n", "<br>") + "</body></html>\r\n";
                    String sendBack = "" +
                        "HTTP/1.1 200 OK\r\n" +
                        "Connection: Keep-Alive\r\n" + // we want to keep the connection open anyway
                        "Content-Length: " + respHtml.length() + "\r\n" +
                        "\r\n" +
                        respHtml;
                    ctx.write(sendBack.getBytes());
                }
            });
        // TODO
        Thread.sleep(100000000);
    }
}
