package vproxy.test.cases;

import org.junit.*;
import vclient.HttpClient;
import vclient.HttpResponse;
import vproxybase.util.BlockCallback;
import vserver.HttpServer;
import vserver.RoutingHandler;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class TestHttpServer {
    private static final int port = 30080;
    private static HttpServer server;

    @BeforeClass
    public static void beforeClass() throws Exception {
        server = HttpServer.create();
        RoutingHandler simpleHandler = rctx -> rctx.response().end(rctx.method() + " " + rctx.uri());

        // simple response
        server.get("/simple-response", simpleHandler);

        // path param
        server.get("/path-1/:path-param", rctx -> rctx.response().end(rctx.param("path-param")));

        // simple filter and response
        server.get("/simple-filter-response", rctx -> rctx.putParam("foo", "bar-s").next());
        server.get("/simple-filter-response", rctx -> rctx.response().end(
            rctx.param("foo") + " - " + rctx.uri()
        ));

        // path param filter
        server.get("/path-2/:path-param-1", rctx -> rctx.putParam("pass", rctx.param("path-param-1")).next());
        server.get("/path-2/:path-param-2", rctx -> rctx.response().end(rctx.param("pass") + " - " + rctx.param("path-param-2")));

        // wildcard
        server.get("/path-3/*", simpleHandler);

        // wildcard filter
        server.get("/path-4/*", rctx -> rctx.putParam("foo", "bar-4").next());
        server.get("/path-4/*", rctx -> rctx.response().end(
            rctx.param("foo") + " - " + rctx.uri()
        ));

        // param and wildcard
        server.get("/path-5/:param/middle/*", rctx -> rctx.response().end(
            rctx.param("param") + " - " + rctx.uri()
        ));

        // param and wildcard filter
        server.get("/path-6/:param/middle/*", rctx -> rctx.putParam("pass", rctx.param("param")).next());
        server.get("/path-6/:param2/middle/*", rctx -> rctx.response().end(
            rctx.param("param") + " - " + rctx.param("pass") + " - " + rctx.uri()
        ));

        server.listen(port);
    }

    @AfterClass
    public static void afterClass() {
        server.close();
    }

    HttpClient client;

    @Before
    public void setUp() {
        client = HttpClient.to("127.0.0.1", port);
    }

    @After
    public void tearDown() {
        client.close();
    }

    interface ExFunction<T, R> {
        R apply(T t) throws Exception;
    }

    @Test
    public void simple() throws Exception {
        ExFunction<String, String> request = uri -> {
            BlockCallback<HttpResponse, IOException> cb = new BlockCallback<>();
            client.get(uri).send(cb::finish);
            var resp = cb.block();
            return resp.bodyAsString();
        };
        String res;
        res = request.apply("/simple-response");
        assertEquals("GET /simple-response", res);

        res = request.apply("/path-1/hh");
        assertEquals("hh", res);
        res = request.apply("/path-1/gg");
        assertEquals("gg", res);

        res = request.apply("/simple-filter-response");
        assertEquals("bar-s - /simple-filter-response", res);

        res = request.apply("/path-2/hh");
        assertEquals("hh - hh", res);
        res = request.apply("/path-2/gg");
        assertEquals("gg - gg", res);

        res = request.apply("/path-3/hh");
        assertEquals("GET /path-3/hh", res);
        res = request.apply("/path-3/gg");
        assertEquals("GET /path-3/gg", res);
        res = request.apply("/path-3/hh/gg");
        assertEquals("GET /path-3/hh/gg", res);

        res = request.apply("/path-4/hh");
        assertEquals("bar-4 - /path-4/hh", res);
        res = request.apply("/path-4/gg");
        assertEquals("bar-4 - /path-4/gg", res);
        res = request.apply("/path-4/hh/gg");
        assertEquals("bar-4 - /path-4/hh/gg", res);

        res = request.apply("/path-5/hh/middle/gg");
        assertEquals("hh - /path-5/hh/middle/gg", res);
        res = request.apply("/path-5/hhgg/middle/gghh");
        assertEquals("hhgg - /path-5/hhgg/middle/gghh", res);
    }

    @Test
    public void test404() throws Exception {
        BlockCallback<HttpResponse, IOException> cb = new BlockCallback<>();
        client.get("/").send(cb::finish);
        var resp = cb.block();

        assertEquals(404, resp.status());
        assertEquals("Cannot GET /\r\n", resp.bodyAsString());

        cb = new BlockCallback<>();
        client.get("/abc").send(cb::finish);
        resp = cb.block();

        assertEquals(404, resp.status());
        assertEquals("Cannot GET /abc\r\n", resp.bodyAsString());
    }
}
