package io.vproxy.ci;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.*;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.redis.client.*;
import io.vproxy.app.app.Application;
import io.vproxy.app.app.Main;
import io.vproxy.base.Config;
import io.vproxy.base.connection.ServerSock;
import io.vproxy.base.util.AnnotationKeys;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.test.cases.TestSSL;
import io.vproxy.vfd.IPPort;
import org.junit.*;
import vjson.JSON;
import vjson.util.ObjectBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@SuppressWarnings("ALL")
public class CI {
    static class ReqIntervalCheckServer {
        private List<Integer> intervals = new ArrayList<>();
        private int interval = -1;
        private long last = -1;
        private NetServer server;
        private final int port;

        ReqIntervalCheckServer(int port) {
            this.port = port;
        }

        public void start() {
            if (server != null)
                return;
            server = vertx.createNetServer().connectHandler(sock -> {
                long foo = last;
                last = System.currentTimeMillis();
                if (foo != -1) {
                    intervals.add((int) (last - foo));
                    if (intervals.size() > 5) {
                        intervals.remove(0);
                    }
                    int sum = 0;
                    for (int i : intervals) {
                        sum += i;
                    }
                    if (intervals.isEmpty()) {
                        interval = -1;
                    } else {
                        interval = sum / intervals.size();
                    }
                }
            }).listen(port);
        }

        public void stop() {
            if (server != null) {
                server.close();
                server = null;
            }
        }

        public int getInterval() {
            return interval;
        }

        public void clear() {
            intervals.clear();
            interval = -1;
            last = -1;
        }
    }

    private static <T> T block(Consumer<Handler<AsyncResult<T>>> f) {
        Throwable[] t = {null};
        Object[] res = {null};
        boolean[] done = {false};
        f.accept(r -> {
            if (r.failed()) {
                t[0] = r.cause();
            } else {
                res[0] = r.result();
            }
            done[0] = true;
        });
        while (!done[0]) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (t[0] != null)
            throw new RuntimeException(t[0]);
        //noinspection unchecked
        return (T) res[0];
    }

    private static Command getCommand(String name) {
        return Command.create(name, 100, 0, 0, 0, false, false);
    }

    private static Command list = getCommand("list");
    private static Command list_detail = getCommand("list-detail");
    private static Command add = getCommand("add");
    private static Command update = getCommand("update");
    private static Command remove = getCommand("remove");

    private static Vertx vertx;
    private static Redis redis;
    private static WebClient webClient;
    private static ReqIntervalCheckServer reqIntervalCheckServer;

    private static int vproxyRESPPort;
    private static int vproxyHTTPPort;

    private static String loopbackNic() {
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (nic.isLoopback()) {
                    return nic.getName();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException();
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        {
            String strPort = Utils.getSystemProperty("vproxy_port", "16309");
            int port = Integer.parseInt(strPort);
            vproxyRESPPort = port;

            strPort = Utils.getSystemProperty("vproxy_http_port", "18880");
            port = Integer.parseInt(strPort);
            vproxyHTTPPort = port;
        }

        String password = Utils.getSystemProperty("vproxy_password");
        if (password == null)
            password = Utils.getSystemProperty("vproxy_password");
        if (password == null)
            password = "123456";

        File confFile = File.createTempFile("vproxy", ".conf");
        confFile.deleteOnExit();
        Files.writeString(confFile.toPath(), "" +
            "System: add resp-controller resp-controller address localhost:" + vproxyRESPPort + " password " + password + "\n" +
            "System: add http-controller http-controller address localhost:" + vproxyHTTPPort);
        if (Utils.getSystemProperty("vproxy_exists") == null) {
            Main.main(new String[]{
                "allowSystemCommandInNonStdIOController",
                "noStdIOController",
                "load", confFile.getAbsolutePath(),
                "noSave"
            });
        }

        vertx = Vertx.vertx();
        redis = Redis.createClient(vertx, new RedisOptions()
            .setEndpoint(SocketAddress.inetSocketAddress(vproxyRESPPort, "127.0.0.1"))
        );
        CI.<Redis>block(f -> redis.connect(f));
        execute(Request.cmd(Command.AUTH).arg(password));

        vertx.createHttpServer().requestHandler(req -> req.response().end("7771")).listen(7771);
        vertx.createHttpServer().requestHandler(req -> req.response().end("7772")).listen(7772);
        vertx.createHttpServer().requestHandler(req -> req.response().end("7773")).listen(7773);
        vertx.createHttpServer().requestHandler(req -> req.response().end("7774")).listen(7774);
        reqIntervalCheckServer = new ReqIntervalCheckServer(6661);

        webClient = WebClient.create(vertx, new WebClientOptions()
            .setKeepAlive(false)
        );
    }

    @AfterClass
    public static void tearDownClass() {
        vertx.close();
    }

    private List<String> tlNames = new ArrayList<>();
    private List<String> socks5Names = new ArrayList<>();
    private List<String> dnsNames = new ArrayList<>();
    private List<String> elgNames = new ArrayList<>();
    private List<String> upsNames = new ArrayList<>();
    private List<String> sgNames = new ArrayList<>();
    private List<String> securgNames = new ArrayList<>();
    private List<String> certKeyNames = new ArrayList<>();

    private String elg0;
    private String elg1;
    private String ups0;

    private WebClient socks5WebClient = null;
    private WebClient h2WebClient = null;
    private NetClient netClient = null;
    private WebClient sslWebClient = null;

    @Before
    public void setUp() {
        elg0 = randomName("elg0");
        execute(createReq(add, "event-loop-group", elg0));
        elgNames.add(elg0);
        checkCreate("event-loop-group", elg0);

        elg1 = randomName("elg1");
        execute(createReq(add, "event-loop-group", elg1));
        elgNames.add(elg1);
        checkCreate("event-loop-group", elg1);

        for (int i = 0; i < 2; ++i) {
            String name = "el0" + i;
            execute(createReq(add, "event-loop", name, "to", "event-loop-group", elg0));
            checkCreate("event-loop", name, "event-loop-group", elg0);
        }
        for (int i = 0; i < 2; ++i) {
            String name = "el1" + i;
            execute(createReq(add, "event-loop", name, "to", "event-loop-group", elg1));
            checkCreate("event-loop", name, "event-loop-group", elg1);
        }

        ups0 = randomName("ups0");
        execute(createReq(add, "upstream", ups0));
        upsNames.add(ups0);
        checkCreate("upstream", ups0);
    }

    @After
    public void tearDown() {
        // remove one another according to dependency
        // remove tl
        for (String tl : tlNames) {
            execute(createReq(remove, "tcp-lb", tl));
            checkRemove("tcp-lb", tl);
        }
        // remove socks5
        for (String socks5 : socks5Names) {
            execute(createReq(remove, "socks5-server", socks5));
            checkRemove("socks5-server", socks5);
        }
        // remove dns
        for (String dns : dnsNames) {
            execute(createReq(remove, "dns-server", dns));
            checkRemove("dns-server", dns);
        }
        // remove upstream
        for (String ups : upsNames) {
            execute(createReq(remove, "upstream", ups));
            checkRemove("upstream", ups);
        }
        // remove server group
        for (String sg : sgNames) {
            execute(createReq(remove, "server-group", sg));
            checkRemove("server-group", sg);
        }
        // remove event loop group
        for (String elg : elgNames) {
            execute(createReq(remove, "event-loop-group", elg));
            checkRemove("event-loop-group", elg);
        }
        // remove security group
        for (String securg : securgNames) {
            execute(createReq(remove, "security-group", securg));
            checkRemove("security-group", securg);
        }
        // remove cert-key
        for (String ck : certKeyNames) {
            execute(createReq(remove, "cert-key", ck));
            checkRemove("cert-key", ck);
        }

        if (socks5WebClient != null) {
            socks5WebClient.close();
        }
        if (h2WebClient != null) {
            h2WebClient.close();
        }
        if (netClient != null) {
            netClient.close();
        }
        if (sslWebClient != null) {
            sslWebClient.close();
        }
    }

    private void initSocks5Client(int port) {
        socks5WebClient = WebClient.create(vertx, new WebClientOptions()
            .setKeepAlive(false)
            .setProxyOptions(new ProxyOptions()
                .setHost("127.0.0.1")
                .setPort(port)
                .setType(ProxyType.SOCKS5)
            )
        );
    }

    private void initH2WebClient() {
        h2WebClient = WebClient.create(vertx, new WebClientOptions()
            .setProtocolVersion(HttpVersion.HTTP_2)
            .setHttp2ClearTextUpgrade(false)
        );
    }

    private void initSSLWebClient() {
        sslWebClient = WebClient.create(vertx, new WebClientOptions()
            .setSsl(true)
            .setTrustAll(true)
            .setVerifyHost(false));
    }

    private void initNetClient() {
        netClient = vertx.createNetClient(new NetClientOptions().setIdleTimeout(10));
    }

    private static Response _execute(Request req) {
        Response r = CI.block(f -> redis.send(req, f));
        if (r.type() == ResponseType.ERROR) {
            throw new RuntimeException(r.toString());
        }
        System.err.println(r);
        return r;
    }

    private static void execute(Request req) {
        Response r = _execute(req);
        assertEquals(ResponseType.BULK, r.type());
        assertEquals("OK", r.toString());
    }

    private static List<String> queryList(Request req) {
        Response r = _execute(req);
        assertEquals(ResponseType.MULTI, r.type());
        List<String> list = new LinkedList<>();
        for (int i = 0; i < r.size(); ++i) {
            Response rr = r.get(i);
            assertEquals(ResponseType.BULK, rr.type());
            list.add(rr.toString());
        }
        return list;
    }

    private static List<List<String>> querySessions(Request req) {
        Response r = _execute(req);
        assertEquals(ResponseType.MULTI, r.type());
        List<List<String>> list = new LinkedList<>();
        for (int i = 0; i < r.size(); ++i) {
            Response rr = r.get(i);
            assertEquals(ResponseType.MULTI, rr.type());
            List<String> ll = new ArrayList<>();
            for (int j = 0; j < rr.size(); ++j) {
                Response rrr = rr.get(j);
                assertEquals(ResponseType.BULK, rrr.type());
                ll.add(rrr.toString());
            }
            list.add(ll);
        }
        return list;
    }

    private static int count(Request req) {
        Response r = _execute(req);
        assertEquals(ResponseType.INTEGER, r.type());
        return r.toInteger();
    }

    private static String randomName(String n) {
        int time = (int) (System.currentTimeMillis() % 3600_000);
        int rand = (int) (Math.random() * 1000);
        return n + "-" + time + "-" + rand;
    }

    private static Request createReq(Command cmd, String... args) {
        Request req = Request.cmd(cmd);
        for (String s : args) {
            req.arg(s);
        }
        return req;
    }

    private static void checkCreate(String resource, String name) {
        List<String> names = queryList(createReq(list, resource));
        assertTrue(names.contains(name));
        List<String> names2 = new ArrayList<>();
        List<String> details = queryList(createReq(list_detail, resource));
        for (String detail : details) {
            names2.add(detail.split(" ")[0]);
        }
        assertEquals(names2, names);
        assertTrue(names2.contains(name));
    }

    private static void checkCreate(String resource, String name, String parentResource, String parentName) {
        List<String> names = queryList(createReq(list, resource, "in", parentResource, parentName));
        assertTrue(names.contains(name));
        List<String> names2 = new ArrayList<>();
        List<String> details = queryList(createReq(list_detail, resource, "in", parentResource, parentName));
        for (String detail : details) {
            names2.add(detail.split(" ")[0]);
        }
        assertEquals(names2, names);
        assertTrue(names2.contains(name));
    }

    private static Map<String, String> getDetail(List<String> details, String name) {
        for (String detail : details) {
            String[] array = detail.split(" ");
            if (!array[0].equals(name)) {
                continue;
            }
            assertEquals("->", array[1]);
            Map<String, String> map = new HashMap<>();
            String last = null;
            for (int i = 2; i < array.length; ++i) {
                if (last == null) {
                    if (Arrays.asList("allow-non-backend", "deny-non-backend").contains(array[i])) {
                        map.put(array[i], "");
                    } else {
                        last = array[i];
                    }
                } else {
                    map.put(last, array[i]);
                    last = null;
                }
            }
            if (last != null)
                throw new IllegalArgumentException("the detail result is invalid: " + detail);
            return map;
        }
        throw new NoSuchElementException();
    }

    private static Map<String, String> getDetail(String resource, String name, String parentResource, String parentName) {
        List<String> details = queryList(createReq(list_detail, resource, "in", parentResource, parentName));
        return getDetail(details, name);
    }

    private static Map<String, String> getDetail(String resource, String name) {
        List<String> details = queryList(createReq(list_detail, resource));
        return getDetail(details, name);
    }

    private static void checkRemove(String resource, String name) {
        List<String> names = queryList(createReq(list, resource));
        assertFalse(names.contains(name));
        names = new ArrayList<>();
        List<String> details = queryList(createReq(list_detail, resource));
        for (String detail : details) {
            names.add(detail.split(" ")[0]);
        }
        assertFalse(names.contains(name));
    }

    private static void checkRemove(String resource, String name, String parentResource, String parentName) {
        List<String> names = queryList(createReq(list, resource, "in", parentResource, parentName));
        assertFalse(names.contains(name));
        names = new ArrayList<>();
        List<String> details = queryList(createReq(list_detail, resource, "in", parentResource, parentName));
        for (String detail : details) {
            names.add(detail.split(" ")[0]);
        }
        assertFalse(names.contains(name));
    }

    private static String request(WebClient webClient, String host, int port) {
        HttpResponse<Buffer> resp = block(f -> webClient.get(port, host, "/").send(f));
        return resp.body().toString();
    }

    private static String request(int port) {
        return request(webClient, "127.0.0.1", port);
    }

    private String requestViaProxy(String host, int port) {
        return request(socks5WebClient, host, port);
    }

    private String requestH2(int port) {
        return request(h2WebClient, "127.0.0.1", port);
    }

    private String requestSSL(int port) {
        return request(sslWebClient, "127.0.0.1", port);
    }

    @Test
    public void simpleLB() throws Exception {
        int port = 7001;
        String lbName = randomName("lb0");
        execute(createReq(add, "tcp-lb", lbName,
            "acceptor-elg", elg0, "event-loop-group", elg1,
            "address", "127.0.0.1:" + port,
            "upstream", ups0));
        tlNames.add(lbName);
        checkCreate("tcp-lb", lbName);
        Map<String, String> detail = getDetail("tcp-lb", lbName);
        assertEquals(elg0, detail.get("acceptor"));
        assertEquals(elg1, detail.get("worker"));
        assertEquals("127.0.0.1:" + port, detail.get("bind"));
        assertEquals(ups0, detail.get("backend"));
        assertEquals("16384", detail.get("in-buffer-size"));
        assertEquals("16384", detail.get("out-buffer-size"));
        assertEquals("tcp", detail.get("protocol"));
        assertEquals("(allow-all)", detail.get("security-group"));

        String sg0 = randomName("sg0");
        execute(createReq(add, "server-group", sg0,
            "timeout", "500", "period", "200", "up", "2", "down", "5",
            "event-loop-group", elg0));
        sgNames.add(sg0);
        checkCreate("server-group", sg0);

        execute(createReq(add, "server-group", sg0, "to", "upstream", ups0, "weight", "10"));
        checkCreate("server-group", sg0, "upstream", ups0);

        execute(createReq(add, "server", "sg7771", "to", "server-group", sg0, "address", "127.0.0.1:7771", "weight", "10"));
        checkCreate("server", "sg7771", "server-group", sg0);
        execute(createReq(add, "server", "sg7772", "to", "server-group", sg0, "address", "127.0.0.1:7772", "weight", "10"));
        checkCreate("server", "sg7772", "server-group", sg0);

        Thread.sleep(500);

        {
            Map<String, String> details = getDetail("server", "sg7771", "server-group", sg0);
            assertEquals("10", details.get("weight"));
            assertEquals("127.0.0.1:7771", details.get("connect-to"));
            assertEquals("UP", details.get("currently"));
        }

        String resp1 = request(7001);
        String resp2 = request(7001);
        if (resp1.equals("7772")) {
            String foo = resp1;
            resp1 = resp2;
            resp2 = foo;
        }
        assertEquals("7771", resp1);
        assertEquals("7772", resp2);
    }

    @Test
    public void simpleSocks5() throws Exception {
        int port = 7002;
        String socks5Name = randomName("s0");
        execute(createReq(add, "socks5-server", socks5Name,
            "acceptor-elg", elg0, "event-loop-group", elg1,
            "address", "127.0.0.1:" + port,
            "upstream", ups0));
        socks5Names.add(socks5Name);
        checkCreate("socks5-server", socks5Name);
        Map<String, String> detail = getDetail("socks5-server", socks5Name);
        assertEquals(elg0, detail.get("acceptor"));
        assertEquals(elg1, detail.get("worker"));
        assertEquals("127.0.0.1:" + port, detail.get("bind"));
        assertEquals(ups0, detail.get("backend"));
        assertEquals("16384", detail.get("in-buffer-size"));
        assertEquals("16384", detail.get("out-buffer-size"));
        assertEquals("(allow-all)", detail.get("security-group"));
        assertEquals("", detail.get("deny-non-backend"));
        assertNull(detail.get("allow-non-backend"));

        String sg0 = randomName("example1");
        execute(createReq(add, "server-group", sg0,
            "timeout", "500", "period", "200", "up", "2", "down", "5",
            "event-loop-group", elg0, "annotations", "{\"" + AnnotationKeys.ServerGroup_HintHost + "\":\"myexample.com\",\"" + AnnotationKeys.ServerGroup_HintPort + "\":\"8080\"}"));
        sgNames.add(sg0);
        checkCreate("server-group", sg0);

        String sg1 = randomName("example2");
        execute(createReq(add, "server-group", sg1,
            "timeout", "500", "period", "200", "up", "2", "down", "5",
            "event-loop-group", elg0, "annotations", "{\"" + AnnotationKeys.ServerGroup_HintHost + "\":\"myexample2.com\",\"" + AnnotationKeys.ServerGroup_HintPort + "\":\"8080\"}"));
        sgNames.add(sg1);
        checkCreate("server-group", sg1);

        execute(createReq(add, "server-group", sg0, "to", "upstream", ups0, "weight", "10"));
        checkCreate("server-group", sg0, "upstream", ups0);
        execute(createReq(add, "server-group", sg1, "to", "upstream", ups0, "weight", "10"));
        checkCreate("server-group", sg1, "upstream", ups0);

        execute(createReq(add, "server", "sg7771", "to", "server-group", sg0, "address", "127.0.0.1:7771", "weight", "10"));
        checkCreate("server", "sg7771", "server-group", sg0);
        execute(createReq(add, "server", "sg7772", "to", "server-group", sg0, "address", "127.0.0.1:7772", "weight", "10"));
        checkCreate("server", "sg7772", "server-group", sg0);
        execute(createReq(add, "server", "sg7773", "to", "server-group", sg1, "address", "127.0.0.1:7773", "weight", "10"));
        checkCreate("server", "sg7773", "server-group", sg1);

        Thread.sleep(500);

        initSocks5Client(port);
        {
            boolean got7771 = false;
            boolean got7772 = false;
            for (int i = 0; i < 10; ++i) {
                String resp = requestViaProxy("myexample.com", 8080);
                assertTrue("7771".equals(resp) || "7772".equals(resp));
                if (resp.equals("7771")) got7771 = true;
                if (resp.equals("7772")) got7772 = true;
            }
            assertTrue(got7771 && got7772);
        }
        {
            for (int i = 0; i < 10; ++i) {
                String resp = requestViaProxy("myexample2.com", 8080);
                assertEquals("7773", resp);
            }
        }
    }

    @Test
    public void dnsServer() throws Exception {
        int port = 10053;
        String dns = randomName("dns");
        execute(createReq(add, "dns-server", dns,
            "event-loop-group", elg1,
            "address", "127.0.0.1:" + port,
            "ttl", "123",
            "upstream", ups0));
        dnsNames.add(dns);
        checkCreate("dns-server", dns);
        Map<String, String> detail = getDetail("dns-server", dns);
        assertEquals(elg1, detail.get("event-loop-group"));
        assertEquals("127.0.0.1:" + port, detail.get("bind"));
        assertEquals(ups0, detail.get("rrsets"));
        assertEquals("123", detail.get("ttl"));

        String domain0 = "example.com";
        String domain1 = "test.com";

        execute(createReq(add, "server-group", domain0,
            "timeout", "500", "period", "200", "up", "2", "down", "5", "protocol", "none",
            "event-loop-group", elg0, "annotations", "{\"" + AnnotationKeys.ServerGroup_HintHost + "\":\"" + domain0 + "\"}"));
        sgNames.add(domain0);
        checkCreate("server-group", domain0);

        execute(createReq(add, "server-group", domain1,
            "timeout", "500", "period", "200", "up", "2", "down", "5", "protocol", "none",
            "event-loop-group", elg0, "annotations", "{\"" + AnnotationKeys.ServerGroup_HintHost + "\":\"" + domain1 + "\"}"));
        sgNames.add(domain1);
        checkCreate("server-group", domain1);

        execute(createReq(add, "server-group", domain0, "to", "upstream", ups0, "weight", "10"));
        checkCreate("server-group", domain0, "upstream", ups0);
        execute(createReq(add, "server-group", domain1, "to", "upstream", ups0, "weight", "10"));
        checkCreate("server-group", domain1, "upstream", ups0);

        execute(createReq(add, "server", "svr1", "to", "server-group", domain0, "address", "10.1.2.3:7771", "weight", "10"));
        checkCreate("server", "svr1", "server-group", domain0);
        execute(createReq(add, "server", "svr2", "to", "server-group", domain0, "address", "10.4.5.6:7772", "weight", "10"));
        checkCreate("server", "svr2", "server-group", domain0);

        execute(createReq(add, "server", "svr3", "to", "server-group", domain1, "address", "10.7.8.9:7773", "weight", "10"));
        checkCreate("server", "svr3", "server-group", domain1);

        Thread.sleep(500); // wait for hc status

        io.vertx.core.dns.DnsClient dnsClient = vertx.createDnsClient(port, "127.0.0.1");
        boolean have1 = false;
        boolean have2 = false;
        for (int i = 0; i < 30; ++i) {
            String res = block(f -> dnsClient.lookup("example.com", f));
            assertTrue(res.equals("10.1.2.3") || res.equals("10.4.5.6"));
            if (res.equals("10.1.2.3")) {
                have1 = true;
            } else {
                have2 = true;
            }
        }
        assertTrue(have1);
        assertTrue(have2);
        for (int i = 0; i < 30; ++i) {
            String res = block(f -> dnsClient.lookup("test.com", f));
            assertEquals("10.7.8.9", res);
        }
    }

    @Test
    public void serverGroupMethod() throws Throwable {
        int port = 7003;
        String lbName = randomName("lb0");
        execute(createReq(add, "tcp-lb", lbName,
            "acceptor-elg", elg0, "event-loop-group", elg1,
            "address", "127.0.0.1:" + port,
            "upstream", ups0));
        tlNames.add(lbName);
        checkCreate("tcp-lb", lbName);

        String sg0 = randomName("sg0");
        execute(createReq(add, "server-group", sg0,
            "timeout", "500", "period", "200", "up", "2", "down", "5",
            "event-loop-group", elg0));
        sgNames.add(sg0);
        checkCreate("server-group", sg0);
        {
            Map<String, String> details = getDetail("server-group", sg0);
            assertEquals("500", details.get("timeout"));
            assertEquals("200", details.get("period"));
            assertEquals("2", details.get("up"));
            assertEquals("5", details.get("down"));
            assertEquals("tcp", details.get("protocol"));
            assertEquals("wrr", details.get("method"));
            assertEquals(elg0, details.get("event-loop-group"));
        }

        execute(createReq(add, "server-group", sg0, "to", "upstream", ups0, "weight", "12"));
        checkCreate("server-group", sg0, "upstream", ups0);
        {
            Map<String, String> details = getDetail("server-group", sg0, "upstream", ups0);
            assertEquals("500", details.get("timeout"));
            assertEquals("200", details.get("period"));
            assertEquals("2", details.get("up"));
            assertEquals("5", details.get("down"));
            assertEquals("tcp", details.get("protocol"));
            assertEquals("wrr", details.get("method"));
            assertEquals(elg0, details.get("event-loop-group"));
            assertEquals("12", details.get("weight"));
        }

        // weight = 2 : 1
        execute(createReq(add, "server", "sg7771", "to", "server-group", sg0, "address", "127.0.0.1:7771", "weight", "10"));
        execute(createReq(add, "server", "sg7772", "to", "server-group", sg0, "address", "127.0.0.1:7772", "weight", "5"));
        checkCreate("server", "sg7771", "server-group", sg0);
        checkCreate("server", "sg7772", "server-group", sg0);

        Thread.sleep(1000);

        {
            int count1 = 0;
            int count2 = 0;
            for (int i = 0; i < 30; ++i) {
                String resp = request(port);
                if (resp.equals("7771")) ++count1;
                else if (resp.equals("7772")) ++count2;
                else fail();
            }
            assertEquals(2.0d, ((double) count1) / count2, 0.1);
        }

        // update to wlc
        execute(createReq(update, "server-group", sg0, "method", "wlc"));
        {
            Map<String, String> details = getDetail("server-group", sg0);
            assertEquals("wlc", details.get("method"));
            details = getDetail("server-group", sg0, "upstream", ups0);
            assertEquals("wlc", details.get("method"));
        }
        // remove server temporarily
        execute(createReq(remove, "server", "sg7772", "from", "server-group", sg0));
        checkRemove("server", "sg7772", "server-group", sg0);

        initNetClient();

        Thread.sleep(1000);

        List<NetSocket> socks7771 = new ArrayList<>();
        for (int i = 0; i < 5; ++i) {
            NetSocket sock = block(f -> netClient.connect(port, "127.0.0.1", f));
            socks7771.add(sock);
        }

        // then add the server back
        // set weight to 20
        execute(createReq(add, "server", "sg7772", "to", "server-group", sg0, "address", "127.0.0.1:7772", "weight", "20"));
        checkCreate("server", "sg7772", "server-group", sg0);

        Thread.sleep(1000);

        List<NetSocket> socks7772 = new ArrayList<>();
        for (int i = 0; i < 10; ++i) {
            NetSocket sock = block(f -> netClient.connect(port, "127.0.0.1", f));
            socks7772.add(sock);
        }

        for (NetSocket socks : socks7771) {
            String[] result = {null};
            Throwable[] err = {null};
            socks.handler(b -> result[0] = b.toString());
            socks.exceptionHandler(t -> err[0] = t);
            socks.closeHandler(v -> err[0] = new Exception("connection closed"));
            socks.write("" +
                "GET / HTTP/1.1\r\n" +
                "Host: 127.0.0.1\r\n" +
                "\r\n");
            while (result[0] == null && err[0] == null) {
                Thread.sleep(1);
            }
            if (err[0] != null) {
                throw err[0];
            }
            String foo = result[0].trim();
            assertTrue(foo.endsWith("7771"));
        }

        for (NetSocket socks : socks7772) {
            String[] result = {null};
            Throwable[] err = {null};
            socks.handler(b -> result[0] = b.toString());
            socks.exceptionHandler(t -> err[0] = t);
            socks.closeHandler(v -> err[0] = new Exception("connection closed"));
            socks.write("" +
                "GET / HTTP/1.1\r\n" +
                "Host: 127.0.0.1\r\n" +
                "\r\n");
            while (result[0] == null && err[0] == null) {
                Thread.sleep(1);
            }
            if (err[0] != null) {
                throw err[0];
            }
            String foo = result[0].trim();
            assertTrue(foo.endsWith("7772"));
        }

        // update to source
        execute(createReq(update, "server-group", sg0, "method", "source"));
        {
            Map<String, String> details = getDetail("server-group", sg0);
            assertEquals("source", details.get("method"));
            details = getDetail("server-group", sg0, "upstream", ups0);
            assertEquals("source", details.get("method"));
        }

        {
            String resp = request(port);
            for (int i = 0; i < 100; ++i) {
                assertEquals(resp, request(port));
            }
        }
    }

    private void weightCheck(int port, String[] strings, int[] weights) {
        int[] cnt = new int[strings.length];
        for (int i = 0; i < 200; ++i) {
            String resp = request(port);
            for (int n = 0; n < strings.length; ++n) {
                if (strings[n].equals(resp)) {
                    ++cnt[n];
                }
            }
        }
        // compare
        for (int i = 0; i < cnt.length; ++i) {
            for (int j = i + 1; j < cnt.length; ++j) {
                if (weights[j] == 0 && cnt[j] != 0) fail("weight of " + strings[j] + " is 0 but count is " + cnt[j]);
                else
                    assertEquals(strings[i] + " / " + strings[j] + " != " + weights[i] + " / " + weights[j] +
                            ", but " + cnt[i] + " / " + cnt[j],
                        ((double) weights[i]) / weights[j], ((double) cnt[i]) / cnt[j], 0.2);
            }
        }
    }

    @Test
    public void changeWeight() throws Exception {
        int port = 7004;
        String lbName = randomName("lb0");
        execute(createReq(add, "tcp-lb", lbName,
            "acceptor-elg", elg0, "event-loop-group", elg1,
            "address", "127.0.0.1:" + port,
            "upstream", ups0));
        tlNames.add(lbName);
        checkCreate("tcp-lb", lbName);

        String sg0 = randomName("sg0");
        execute(createReq(add, "server-group", sg0,
            "timeout", "500", "period", "200", "up", "2", "down", "5",
            "event-loop-group", elg0));
        sgNames.add(sg0);
        checkCreate("server-group", sg0);

        execute(createReq(add, "server-group", sg0, "to", "upstream", ups0, "weight", "10"));
        checkCreate("server-group", sg0, "upstream", ups0);

        String sg1 = randomName("sg1");
        execute(createReq(add, "server-group", sg1,
            "timeout", "500", "period", "200", "up", "2", "down", "5",
            "event-loop-group", elg0));
        sgNames.add(sg1);
        checkCreate("server-group", sg1);

        execute(createReq(add, "server-group", sg1, "to", "upstream", ups0, "weight", "5"));
        checkCreate("server-group", sg1, "upstream", ups0);

        execute(createReq(add, "server", "sg7771", "to", "server-group", sg0, "address", "127.0.0.1:7771", "weight", "10"));
        checkCreate("server", "sg7771", "server-group", sg0);
        execute(createReq(add, "server", "sg7772", "to", "server-group", sg0, "address", "127.0.0.1:7772", "weight", "5"));
        checkCreate("server", "sg7772", "server-group", sg0);
        execute(createReq(add, "server", "sg7773", "to", "server-group", sg1, "address", "127.0.0.1:7773", "weight", "10"));
        checkCreate("server", "sg7773", "server-group", sg1);
        execute(createReq(add, "server", "sg7774", "to", "server-group", sg1, "address", "127.0.0.1:7774", "weight", "5"));
        checkCreate("server", "sg7774", "server-group", sg1);

        Thread.sleep(500);

        weightCheck(port, new String[]{"7771", "7772", "7773", "7774"}, new int[]{4, 2, 2, 1});

        execute(createReq(update, "server", "sg7771", "in", "server-group", sg0, "weight", "15"));
        execute(createReq(update, "server", "sg7772", "in", "server-group", sg0, "weight", "10"));
        weightCheck(port, new String[]{"7771", "7772", "7773", "7774"}, new int[]{18, 12, 10, 5});

        execute(createReq(update, "server", "sg7774", "in", "server-group", sg1, "weight", "10"));
        weightCheck(port, new String[]{"7771", "7772", "7773", "7774"}, new int[]{12, 8, 5, 5});

        execute(createReq(update, "server-group", sg1, "in", "upstream", ups0, "weight", "15"));
        weightCheck(port, new String[]{"7771", "7772", "7773", "7774"}, new int[]{12, 8, 15, 15});

        execute(createReq(remove, "server-group", sg1, "from", "upstream", ups0));
        checkRemove("server-group", sg1, "upstream", ups0);
        weightCheck(port, new String[]{"7771", "7772"}, new int[]{3, 2});
    }

    @Test
    public void changeHealthCheck() throws Exception {
        reqIntervalCheckServer.start();
        String sg0 = randomName("sg0");
        execute(createReq(add, "server-group", sg0,
            "timeout", "500", "period", "200", "up", "3", "down", "4",
            "event-loop-group", elg0));
        sgNames.add(sg0);
        checkCreate("server-group", sg0);
        long timeCreated = System.currentTimeMillis();
        execute(createReq(add, "server", "sg6661", "to", "server-group", sg0, "address", "localhost:6661", "weight", "10"));
        checkCreate("server", "sg6661", "server-group", sg0);

        {
            Map<String, String> detail = getDetail("server", "sg6661", "server-group", sg0);
            assertEquals("DOWN", detail.get("currently"));
            assertEquals("127.0.0.1:6661", detail.get("connect-to"));
            assertEquals("localhost", detail.get("host"));
        }
        Supplier<String> getStatus = () -> getDetail("server", "sg6661", "server-group", sg0).get("currently");

        {
            long sleep = timeCreated + 300 - System.currentTimeMillis();
            if (sleep > 0) {
                Thread.sleep(sleep);
                assertEquals("DOWN", getStatus.get());
            }
        }

        Thread.sleep(500);

        assertEquals("UP", getStatus.get());
        assertEquals(200, reqIntervalCheckServer.getInterval(), 50/*the timer may not be so accurate*/);

        reqIntervalCheckServer.stop();
        Thread.sleep(500);
        assertEquals("UP", getStatus.get());
        Thread.sleep(500);
        assertEquals("DOWN", getStatus.get());

        // update
        long timeStarted = System.currentTimeMillis();
        execute(createReq(update, "server-group", sg0, "timeout", "500", "period", "400", "up", "2", "down", "5"));
        reqIntervalCheckServer.clear();
        reqIntervalCheckServer.start();
        assertEquals("DOWN", getStatus.get());
        {
            long sleep = timeStarted + 500 - System.currentTimeMillis();
            if (sleep > 0) {
                Thread.sleep(sleep);
                assertEquals("DOWN", getStatus.get());
            }
        }
        Thread.sleep(700);
        assertEquals("UP", getStatus.get());
        assertEquals(400, reqIntervalCheckServer.getInterval(), 20/*the timer may not be so accurate*/);

        reqIntervalCheckServer.stop();
        Thread.sleep(1500);
        assertEquals("UP", getStatus.get());
        Thread.sleep(1500);
        assertEquals("DOWN", getStatus.get());
    }

    @Test
    public void updateLBAndSocks5AndCreateUpdateSecurityGroup() throws Exception {
        int lbPort = 7005;
        int socks5Port = 7006;

        String lbName = randomName("lb0");
        execute(createReq(add, "tcp-lb", lbName,
            "acceptor-elg", elg0, "event-loop-group", elg1,
            "address", "127.0.0.1:" + lbPort,
            "upstream", ups0));
        tlNames.add(lbName);
        checkCreate("tcp-lb", lbName);

        String socks5Name = randomName("s0");
        execute(createReq(add, "socks5-server", socks5Name,
            "acceptor-elg", elg0, "event-loop-group", elg1,
            "address", "127.0.0.1:" + socks5Port,
            "upstream", ups0));
        socks5Names.add(socks5Name);
        checkCreate("socks5-server", socks5Name);

        String sg0 = randomName("example");
        execute(createReq(add, "server-group", sg0,
            "timeout", "500", "period", "200", "up", "2", "down", "5",
            "event-loop-group", elg0, "annotations", "{\"" + AnnotationKeys.ServerGroup_HintHost + "\":\"myexample.com\",\"" + AnnotationKeys.ServerGroup_HintPort + "\":\"8080\"}"));
        sgNames.add(sg0);
        checkCreate("server-group", sg0);

        execute(createReq(add, "server-group", sg0, "to", "upstream", ups0, "weight", "10"));
        checkCreate("server-group", sg0, "upstream", ups0);

        execute(createReq(add, "server", "sg7771", "to", "server-group", sg0, "address", "127.0.0.1:7771", "weight", "10"));
        checkCreate("server", "sg7771", "server-group", sg0);

        initSocks5Client(socks5Port);

        Thread.sleep(500);

        assertEquals("7771", request(lbPort));
        assertEquals("7771", requestViaProxy("myexample.com", 8080));

        // update allow-non-backend
        execute(createReq(update, "socks5-server", socks5Name, "allow-non-backend"));
        Map<String, String> details;
        {
            details = getDetail("socks5-server", socks5Name);
            assertEquals("", details.get("allow-non-backend"));
        }
        assertEquals("7771", requestViaProxy("myexample.com", 8080));
        assertEquals("7772", requestViaProxy("127.0.0.1", 7772));

        // update in/out
        execute(createReq(update, "tcp-lb", lbName, "in-buffer-size", "3"));
        {
            details = getDetail("tcp-lb", lbName);
            assertEquals("3", details.get("in-buffer-size"));
        }
        execute(createReq(update, "tcp-lb", lbName, "out-buffer-size", "2"));
        {
            details = getDetail("tcp-lb", lbName);
            assertEquals("2", details.get("out-buffer-size"));
        }
        execute(createReq(update, "socks5-server", socks5Name, "in-buffer-size", "3"));
        {
            details = getDetail("socks5-server", socks5Name);
            assertEquals("3", details.get("in-buffer-size"));
        }
        execute(createReq(update, "socks5-server", socks5Name, "out-buffer-size", "2"));
        {
            details = getDetail("socks5-server", socks5Name);
            assertEquals("2", details.get("out-buffer-size"));
        }

        // security-group
        // add one security group
        String secg0 = randomName("secg0");
        execute(createReq(add, "security-group", secg0, "default", "allow"));
        securgNames.add(secg0);
        checkCreate("security-group", secg0);
        {
            details = getDetail("security-group", secg0);
            assertEquals("allow", details.get("default"));
        }

        // update lb and socks5
        execute(createReq(update, "tcp-lb", lbName, "security-group", secg0));
        {
            details = getDetail("tcp-lb", lbName);
            assertEquals(secg0, details.get("security-group"));
        }
        execute(createReq(update, "socks5-server", socks5Name, "security-group", secg0));
        {
            details = getDetail("socks5-server", socks5Name);
            assertEquals(secg0, details.get("security-group"));
        }
        assertEquals("7771", request(lbPort));
        assertEquals("7771", requestViaProxy("myexample.com", 8080));

        // update the default
        execute(createReq(update, "security-group", secg0, "default", "deny"));
        {
            details = getDetail("security-group", secg0);
            assertEquals("deny", details.get("default"));
        }
        try {
            request(lbPort);
            fail();
        } catch (Exception ignore) {
        }
        try {
            requestViaProxy("myexample.com", 8080);
            fail();
        } catch (Exception ignore) {
        }

        // add rule to allow lb
        String ruleLBName = randomName("secgr-lb");
        execute(createReq(add, "security-group-rule", ruleLBName, "to", "security-group", secg0,
            "network", "127.0.0.1/32", "protocol", "TCP", "port-range", lbPort + "," + lbPort, "default", "allow"));
        checkCreate("security-group-rule", ruleLBName, "security-group", secg0);
        {
            details = getDetail("security-group-rule", ruleLBName, "security-group", secg0);
            assertEquals("127.0.0.1/32", details.get("allow"));
            assertEquals("TCP", details.get("protocol"));
            assertEquals("[" + lbPort + "," + lbPort + "]", details.get("port"));
        }
        assertEquals("7771", request(lbPort));
        try {
            requestViaProxy("myexample.com", 8080);
            fail();
        } catch (Exception ignore) {
        }

        // add rule to allow socks5
        String ruleSocksName = randomName("secgr-socks5");
        execute(createReq(add, "security-group-rule", ruleSocksName, "to", "security-group", secg0,
            "network", "127.0.0.1/32", "protocol", "TCP", "port-range", socks5Port + "," + socks5Port, "default", "allow"));
        checkCreate("security-group-rule", ruleSocksName, "security-group", secg0);

        assertEquals("7771", request(lbPort));
        assertEquals("7771", requestViaProxy("myexample.com", 8080));

        // remove rule of lb
        execute(createReq(remove, "security-group-rule", ruleLBName, "from", "security-group", secg0));
        checkRemove("security-group-rule", ruleLBName, "security-group", secg0);

        try {
            assertEquals("7771", request(lbPort));
            fail();
        } catch (Exception ignore) {
        }
        assertEquals("7771", requestViaProxy("myexample.com", 8080));

        // remove rule of socks5
        execute(createReq(remove, "security-group-rule", ruleSocksName, "from", "security-group", secg0));
        checkRemove("security-group-rule", ruleSocksName, "security-group", secg0);

        try {
            request(lbPort);
            fail();
        } catch (Exception ignore) {
        }
        try {
            requestViaProxy("myexample.com", 8080);
            fail();
        } catch (Exception ignore) {
        }

        // set rule to default allow, and deny lb
        execute(createReq(update, "security-group", secg0, "default", "allow"));
        {
            details = getDetail("security-group", secg0);
            assertEquals("allow", details.get("default"));
        }
        execute(createReq(add, "security-group-rule", ruleLBName, "to", "security-group", secg0,
            "network", "127.0.0.1/32", "protocol", "TCP", "port-range", lbPort + "," + lbPort, "default", "deny"));
        checkCreate("security-group-rule", ruleLBName, "security-group", secg0);

        try {
            request(lbPort);
            fail();
        } catch (Exception ignore) {
        }
        assertEquals("7771", requestViaProxy("myexample.com", 8080));

        // set secg to (allow-all)
        execute(createReq(update, "tcp-lb", lbName, "security-group", "(allow-all)"));
        {
            details = getDetail("tcp-lb", lbName);
            assertEquals("(allow-all)", details.get("security-group"));
        }
        assertEquals("7771", request(lbPort));

        execute(createReq(update, "socks5-server", socks5Name, "security-group", "(allow-all)"));
        {
            details = getDetail("socks5-server", socks5Name);
            assertEquals("(allow-all)", details.get("security-group"));
        }
        assertEquals("7771", requestViaProxy("myexample.com", 8080));
    }

    // TODO is not supported now
    @Ignore
    @Test
    public void channelAndStateAndStatistics() throws Exception {
        int port = 7007;
        String lbName = randomName("lb0");
        execute(createReq(add, "tcp-lb", lbName,
            "acceptor-elg", elg0, "event-loop-group", elg1,
            "address", "127.0.0.1:" + port,
            "upstream", ups0));
        tlNames.add(lbName);
        checkCreate("tcp-lb", lbName);

        String sg0 = randomName("sg0");
        execute(createReq(add, "server-group", sg0,
            "timeout", "500", "period", "200", "up", "2", "down", "5",
            "event-loop-group", elg0));
        sgNames.add(sg0);
        checkCreate("server-group", sg0);

        execute(createReq(add, "server-group", sg0, "to", "upstream", ups0, "weight", "10"));
        checkCreate("server-group", sg0, "upstream", ups0);

        execute(createReq(add, "server", "sg7771", "to", "server-group", sg0, "address", "127.0.0.1:7771", "weight", "10"));
        checkCreate("server", "sg7771", "server-group", sg0);
        execute(createReq(add, "server", "sg7772", "to", "server-group", sg0, "address", "127.0.0.1:7772", "weight", "5"));
        checkCreate("server", "sg7772", "server-group", sg0);

        Thread.sleep(500);

        // server-sock
        assertEquals(1, count(createReq(list, "server-sock", "in", "el", "el00", "in", "elg", elg0)));
        assertEquals(1, count(createReq(list, "server-sock", "in", "el", "el01", "in", "elg", elg0)));
        assertEquals(Collections.singletonList("127.0.0.1:" + port), queryList(createReq(list_detail, "server-sock", "in", "el", "el00", "in", "elg", elg0)));
        assertEquals(Collections.singletonList("127.0.0.1:" + port), queryList(createReq(list_detail, "server-sock", "in", "el", "el01", "in", "elg", elg0)));

        assertEquals(2, count(createReq(list, "server-sock", "in", "tcp-lb", lbName)));
        assertEquals(Arrays.asList(
            "127.0.0.1:" + port,
            "127.0.0.1:" + port
        ), queryList(createReq(list_detail, "server-sock", "in", "tcp-lb", lbName)));

        // test socks5 here
        {
            int socks5Port = 7002;
            String socks5Name = randomName("s0");
            execute(createReq(add, "socks5-server", socks5Name,
                "acceptor-elg", elg0, "event-loop-group", elg1,
                "address", "127.0.0.1:" + socks5Port,
                "upstream", ups0));
            socks5Names.add(socks5Name);
            checkCreate("socks5-server", socks5Name);

            assertEquals(2, count(createReq(list, "server-sock", "in", "socks5-server", socks5Name)));
            assertEquals(Arrays.asList(
                "127.0.0.1:" + socks5Port,
                "127.0.0.1:" + socks5Port
            ), queryList(createReq(list_detail, "server-sock", "in", "socks5-server", socks5Name)));

            execute(createReq(remove, "socks5-server", socks5Name));
            checkRemove("socks5-server", socks5Name);
            socks5Names.remove(socks5Name);
        }

        // accepted-conn-count = 0
        {
            assertEquals(0,
                count(createReq(list, "accepted-conn-count",
                    "in", "server-sock", "127.0.0.1:" + port, "in", "el", "el00", "in", "elg", elg0)) +
                    count(createReq(list, "accepted-conn-count",
                        "in", "server-sock", "127.0.0.1:" + port, "in", "el", "el01", "in", "elg", elg0))
            );
        }

        initNetClient();
        NetSocket sock1 = block(f -> netClient.connect(port, "localhost", f));
        NetSocket sock2 = block(f -> netClient.connect(port, "localhost", f));
        NetSocket sock3 = block(f -> netClient.connect(port, "localhost", f));

        // check sessions and connections
        {
            // elg
            int countConnections = count(createReq(list, "connection", "in", "el", "el10", "in", "elg", elg1))
                + count(createReq(list, "connection", "in", "el", "el11", "in", "elg", elg1));
            assertEquals(6, countConnections);
            List<String> connections = new ArrayList<>();
            connections.addAll(queryList(createReq(list_detail, "connection", "in", "el", "el10", "in", "elg", elg1)));
            connections.addAll(queryList(createReq(list_detail, "connection", "in", "el", "el11", "in", "elg", elg1)));
            assertEquals(6, connections.size());
            assertTrue(connections.contains("127.0.0.1:" + sock1.localAddress().port() + "/127.0.0.1:" + port + "[ESTABLISHED]"));
            assertTrue(connections.contains("127.0.0.1:" + sock2.localAddress().port() + "/127.0.0.1:" + port + "[ESTABLISHED]"));
            assertTrue(connections.contains("127.0.0.1:" + sock3.localAddress().port() + "/127.0.0.1:" + port + "[ESTABLISHED]"));

            // lb
            int countSessions = count(createReq(list, "session", "in", "tcp-lb", lbName));
            countConnections = count(createReq(list, "connection", "in", "tcp-lb", lbName));
            assertEquals(3, countSessions);
            assertEquals(6, countConnections);
            connections = queryList(createReq(list_detail, "connection", "in", "tcp-lb", lbName));
            assertEquals(6, connections.size());
            assertTrue(connections.contains("127.0.0.1:" + port + "/127.0.0.1:" + sock1.localAddress().port() + "[ESTABLISHED]"));
            assertTrue(connections.contains("127.0.0.1:" + port + "/127.0.0.1:" + sock2.localAddress().port() + "[ESTABLISHED]"));
            assertTrue(connections.contains("127.0.0.1:" + port + "/127.0.0.1:" + sock3.localAddress().port() + "[ESTABLISHED]"));
            assertEquals(3, querySessions(createReq(list_detail, "session", "in", "tcp-lb", lbName)).size());
            var sessionsResult = querySessions(createReq(list_detail, "session", "in", "tcp-lb", lbName));
            assertTrue(sessionsResult.stream().flatMap(Collection::stream).collect(Collectors.toSet()).containsAll(connections));

            // server
            assertEquals(2, count(createReq(list, "connection", "in", "server", "sg7771", "in", "server-group", sg0)));
            assertEquals(1, count(createReq(list, "connection", "in", "server", "sg7772", "in", "server-group", sg0)));
            assertEquals(2, queryList(createReq(list_detail, "connection", "in", "server", "sg7771", "in", "server-group", sg0)).size());
            assertEquals(1, queryList(createReq(list_detail, "connection", "in", "server", "sg7772", "in", "server-group", sg0)).size());
        }

        // accepted-conn-count = 3
        {
            assertEquals(3,
                count(createReq(list, "accepted-conn-count",
                    "in", "server-sock", "127.0.0.1:" + port, "in", "el", "el00", "in", "elg", elg0)) +
                    count(createReq(list, "accepted-conn-count",
                        "in", "server-sock", "127.0.0.1:" + port, "in", "el", "el01", "in", "elg", elg0))
            );
        }

        // bytes-in and bytes-out are not easy to be tested
        // we just leave here a
        // TODO
    }

    @Test
    public void lbWithProtocol() throws Exception {
        int lbPort = 7007;

        String lbName = randomName("lb0");
        execute(createReq(add, "tcp-lb", lbName,
            "acceptor-elg", elg0, "event-loop-group", elg1,
            "address", "127.0.0.1:" + lbPort,
            "upstream", ups0,
            "protocol", "h2"));
        tlNames.add(lbName);
        checkCreate("tcp-lb", lbName);
        assertEquals("h2", getDetail("tcp-lb", lbName).get("protocol"));

        String sg0 = "sg0";
        execute(createReq(add, "server-group", sg0,
            "timeout", "500", "period", "200", "up", "2", "down", "5",
            "event-loop-group", elg0));
        sgNames.add(sg0);
        checkCreate("server-group", sg0);

        execute(createReq(add, "server-group", sg0, "to", "upstream", ups0, "weight", "10"));
        checkCreate("server-group", sg0, "upstream", ups0);

        execute(createReq(add, "server", "sg7771", "to", "server-group", sg0, "address", "127.0.0.1:7771", "weight", "10"));
        checkCreate("server", "sg7771", "server-group", sg0);

        execute(createReq(add, "server", "sg7772", "to", "server-group", sg0, "address", "127.0.0.1:7772", "weight", "10"));
        checkCreate("server", "sg7772", "server-group", sg0);

        // wait for health check
        Thread.sleep(500);

        initH2WebClient();

        int got1 = 0;
        int got2 = 0;
        for (int i = 0; i < 100; ++i) {
            String resp = requestH2(lbPort);
            assertTrue(resp.equals("7771") || resp.equals("7772"));
            if (resp.equals("7771")) {
                ++got1;
            } else {
                ++got2;
            }
        }
        assertEquals(50, got1);
        assertEquals(50, got2);
    }

    @Test
    public void defaultEventLoops() {
        List<String> ls = queryList(createReq(list_detail, "event-loop-group"));
        assertEquals(3 + 2 /*two event loop groups created in the CI test*/, ls.size());
        { // only use names
            List<String> newLs = new ArrayList<>();
            for (String s : ls) {
                newLs.add(s.split(" ")[0]);
            }
            ls = newLs;
        }
        assertTrue(ls.contains(Application.DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME));
        assertTrue(ls.contains(Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME));
        assertTrue(ls.contains(Application.DEFAULT_CONTROL_EVENT_LOOP_GROUP_NAME));

        ls = queryList(createReq(list, "event-loop", "in", "event-loop-group", Application.DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME));
        int cnt = Runtime.getRuntime().availableProcessors();
        if (ServerSock.supportReusePort() && Config.supportReusePortLB()) {
            assertEquals(cnt, ls.size());
        } else {
            assertEquals(1, ls.size());
            assertTrue(ls.contains(Application.DEFAULT_ACCEPTOR_EVENT_LOOP_NAME));
        }

        ls = queryList(createReq(list, "event-loop", "in", "event-loop-group", Application.DEFAULT_CONTROL_EVENT_LOOP_GROUP_NAME));
        assertEquals(1, ls.size());
        assertTrue(ls.contains(Application.DEFAULT_CONTROL_EVENT_LOOP_NAME));

        ls = queryList(createReq(list, "event-loop", "in", "event-loop-group", Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME));
        assertEquals(cnt, ls.size());
        for (int i = 0; i < cnt; ++i) {
            assertTrue(ls.contains(Application.DEFAULT_WORKER_EVENT_LOOP_NAME_PREFIX + i + Application.DEFAULT_WORKER_EVENT_LOOP_NAME_SUFFIX));
        }

        int lbPort = 7001;
        String lbName = randomName("lb0");
        execute(createReq(add, "tcp-lb", lbName, "address", "127.0.0.1:" + lbPort, "upstream", ups0));
        tlNames.add(lbName);
        Map<String, String> details = getDetail("tcp-lb", lbName);
        assertEquals(Application.DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME, details.get("acceptor"));
        assertEquals(Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME, details.get("worker"));

        int socks5Port = 7002;
        String socks5Name = randomName("socks5-0");
        execute(createReq(add, "socks5-server", socks5Name, "address", "127.0.0.1:" + socks5Port, "upstream", ups0));
        socks5Names.add(socks5Name);
        details = getDetail("tcp-lb", lbName);
        assertEquals(Application.DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME, details.get("acceptor"));
        assertEquals(Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME, details.get("worker"));

        String serverGroupName = randomName("sg0");
        execute(createReq(add, "server-group", serverGroupName, "timeout", "1000", "period", "1000", "up", "2", "down", "3"));
        sgNames.add(serverGroupName);
        details = getDetail("server-group", serverGroupName);
        assertEquals(Application.DEFAULT_CONTROL_EVENT_LOOP_GROUP_NAME, details.get("event-loop-group"));
    }

    @Test
    public void sslTcpLB() throws Exception {
        int lbPort = 7008;

        // add cert-key
        File tmpCertFile = File.createTempFile("cert", ".pem");
        tmpCertFile.deleteOnExit();
        File tmpKeyFile = File.createTempFile("key", ".pem");
        tmpKeyFile.deleteOnExit();

        FileOutputStream fos = new FileOutputStream(tmpCertFile);
        fos.write(TestSSL.TEST_CERT.getBytes());
        fos.flush();
        fos.close();
        fos = new FileOutputStream(tmpKeyFile);
        fos.write(TestSSL.TEST_KEY.getBytes());
        fos.flush();
        fos.close();

        String certKeyName = randomName("cert-key");

        execute(createReq(add, "cert-key", certKeyName, "cert", tmpCertFile.getAbsolutePath(), "key", tmpKeyFile.getAbsolutePath()));
        certKeyNames.add(certKeyName);
        checkCreate("cert-key", certKeyName);

        // init lb
        String lbName = randomName("sslLB");
        execute(createReq(add, "tcp-lb", lbName,
            "acceptor-elg", elg0, "event-loop-group", elg1,
            "address", "127.0.0.1:" + lbPort,
            "upstream", ups0,
            "protocol", "http",
            "cert-key", certKeyName));
        tlNames.add(lbName);
        checkCreate("tcp-lb", lbName);

        String sg0 = "sg0";
        execute(createReq(add, "server-group", sg0,
            "timeout", "500", "period", "200", "up", "2", "down", "5",
            "event-loop-group", elg0));
        sgNames.add(sg0);
        checkCreate("server-group", sg0);

        execute(createReq(add, "server-group", sg0, "to", "upstream", ups0, "weight", "10"));
        checkCreate("server-group", sg0, "upstream", ups0);

        execute(createReq(add, "server", "sg7771", "to", "server-group", sg0, "address", "127.0.0.1:7771", "weight", "10"));
        checkCreate("server", "sg7771", "server-group", sg0);

        // wait for health check
        Thread.sleep(500);

        initSSLWebClient();

        String res = requestSSL(lbPort);
        assertEquals("7771", res);
    }

    // ====================================
    // ====================================
    // ====================================
    // ====================================
    // ====================================
    // ====================================
    // ====================================
    // ====================================
    // ====================================
    // ====================================
    // ====================================
    // ====================================
    // ====================================
    // ====================================
    // ====================================
    // HttpController api

    private static void getMetaData(boolean isUpdate, Field[] fields, Map<String, Class> requiredKeys, Map<String, Class> optionalKeys, Map<String, List<String>> _stickyKeys) {
        Map<Integer, List<String>> stickyKeys = new HashMap<>();
        for (Field f : fields) {
            String key = f.getName();
            if (isUpdate) {
                if (!f.isAnnotationPresent(Entities.Modifiable.class)) {
                    continue;
                }
            }
            if (isUpdate) {
                optionalKeys.put(f.getName(), f.getType());
            } else {
                if (f.isAnnotationPresent(Entities.Optional.class)) {
                    optionalKeys.put(f.getName(), f.getType());
                } else {
                    requiredKeys.put(f.getName(), f.getType());
                }
            }
            if (f.isAnnotationPresent(Entities.Sticky.class)) {
                Entities.Sticky s = f.getAnnotation(Entities.Sticky.class);
                int v = s.value();
                List<String> keys = stickyKeys.get(v);
                if (keys == null) {
                    keys = new LinkedList<>();
                    stickyKeys.put(v, keys);
                }
                keys.add(f.getName());
            }
        }

        for (List<String> keys : stickyKeys.values()) {
            for (String key : keys) {
                _stickyKeys.put(key, keys);
            }
        }
    }

    private static void putValue(ObjectBuilder ob, String key, Class<?> valueType, Object value) {
        if (valueType == String.class) {
            ob.put(key, (String) value);
        } else if (valueType == int.class) {
            ob.put(key, (int) value);
        } else if (valueType == String[].class) {
            ob.putArray(key, a -> {
                String[] foo = (String[]) value;
                for (int i = 0; i < foo.length; ++i) {
                    a.add(foo[i]);
                }
            });
        } else if (valueType == boolean.class) {
            ob.put(key, (boolean) value);
        } else if (valueType.isEnum() || valueType == IPPort.class) {
            ob.put(key, value.toString());
        } else if (valueType == JSON.Object.class) {
            ob.putInst(key, (JSON.Object) value);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private static int nextPort = 30000;

    private static int getNextListeningPort() {
        int r = ++nextPort;
        if (r >= 60000) {
            nextPort = 30000;
        }
        return r;
    }

    private static void putRandom(ObjectBuilder ob, String key, Class<?> valueType) {
        if (valueType == String.class) {
            var sb = new StringBuilder();
            int len = (int) (Math.random() * 17) + 3;
            char[] sample = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-$".toCharArray();
            for (int i = 0; i < len; ++i) {
                sb.append(sample[(int) (Math.random() * sample.length)]);
            }
            String input = sb.toString();
            putValue(ob, key, valueType, input);
        } else if (valueType == int.class) {
            int input = (int) (Math.random() * 100) + 1;
            putValue(ob, key, valueType, input);
        } else if (valueType.isEnum()) {
            Object[] enums = valueType.getEnumConstants();
            Object v = enums[(int) (Math.random() * enums.length)];
            putValue(ob, key, valueType, v);
        } else if (valueType == IPPort.class) {
            int port = getNextListeningPort();
            putValue(ob, key, valueType, "0.0.0.0:" + port);
        } else if (valueType == boolean.class) {
            putValue(ob, key, valueType, (Math.random() < 0.5) ? true : false);
        } else if (valueType == JSON.Object.class) {
            putValue(ob, key, JSON.Object.class, new ObjectBuilder().put(randomName("key"), randomName("value")).build());
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private static void initValue(Case c, ObjectBuilder ob, String key, Map<String, Class> typeMap, Map<String, Object> params, Set<String> alreadyAddedStickyKeys, Map<String, List<String>> stickyKeys) {
        if (stickyKeys.containsKey(key)) {
            var keys = stickyKeys.get(key);
            for (var sk : keys) {
                var type = typeMap.get(sk);
                if (params.containsKey(sk)) {
                    putValue(ob, sk, type, params.get(sk));
                } else {
                    putRandom(ob, sk, type);
                }
                alreadyAddedStickyKeys.add(sk);
            }
        } else {
            var type = typeMap.get(key);
            if (params.containsKey(key)) {
                putValue(ob, key, type, params.get(key));
            } else {
                putRandom(ob, key, type);
            }
        }
    }

    private static class Case {
        JSON.Object body;
    }

    private static void helper(List<int[]> combinations, int data[], int start, int end, int index) {
        if (index == data.length) {
            int[] combination = data.clone();
            combinations.add(combination);
        } else if (start <= end) {
            data[index] = start;
            helper(combinations, data, start + 1, end, index + 1);
            helper(combinations, data, start + 1, end, index);
        }
    }

    private static List<int[]> comb(int n, int r) {
        List<int[]> combinations = new ArrayList<>();
        helper(combinations, new int[r], 0, n - 1, 0);
        return combinations;
    }

    private static List<Case> buildCreateList(Field[] fields, Map<String, Object> params) {
        Map<String, Class> requiredKeys = new HashMap<>();
        Map<String, Class> optionalKeys = new HashMap<>();
        Map<String, List<String>> stickyKeys = new HashMap<>();
        getMetaData(false, fields, requiredKeys, optionalKeys, stickyKeys);

        String[] optionalKeysArray = new String[optionalKeys.size()];
        {
            int i = 0;
            for (String key : optionalKeys.keySet()) {
                optionalKeysArray[i++] = key;
            }
        }

        List<Case> ret = new LinkedList<>();
        {
            var c = new Case();
            var ob = new ObjectBuilder();
            var addedStickyKeys = new HashSet<String>();
            for (String key : requiredKeys.keySet()) {
                initValue(c, ob, key, requiredKeys, params, addedStickyKeys, stickyKeys);
            }
            c.body = ob.build();
            ret.add(c);
        }

        for (int optionalKeyCnt = 1; optionalKeyCnt <= optionalKeysArray.length; ++optionalKeyCnt) {
            List<int[]> comb = comb(optionalKeysArray.length, optionalKeyCnt);
            for (int[] seq : comb) {
                var c = new Case();
                var ob = new ObjectBuilder();
                var addedStickyKeys = new HashSet<String>();
                for (String key : requiredKeys.keySet()) {
                    initValue(c, ob, key, requiredKeys, params, addedStickyKeys, stickyKeys);
                }
                for (int i : seq) {
                    String key = optionalKeysArray[i];
                    initValue(c, ob, key, optionalKeys, params, addedStickyKeys, stickyKeys);
                }
                c.body = ob.build();
                ret.add(c);
            }
        }

        return ret;
    }

    private static List<Case> buildUpdateList(Field[] fields, Map<String, Object> params) {
        Map<String, Class> optionalKeys = new HashMap<>();
        Map<String, List<String>> stickyKeys = new HashMap<>();
        getMetaData(true, fields, null, optionalKeys, stickyKeys);

        String[] optionalKeysArray = new String[optionalKeys.size()];
        {
            int i = 0;
            for (String key : optionalKeys.keySet()) {
                optionalKeysArray[i++] = key;
            }
        }

        List<Case> foo = new LinkedList<>();

        for (int optionalKeyCnt = 0; optionalKeyCnt <= optionalKeysArray.length; ++optionalKeyCnt) {
            List<int[]> comb = comb(optionalKeysArray.length, optionalKeyCnt);
            for (int[] seq : comb) {
                var c = new Case();
                var ob = new ObjectBuilder();
                var addedStickyKeys = new HashSet<String>();
                for (int i : seq) {
                    String key = optionalKeysArray[i];
                    initValue(c, ob, key, optionalKeys, params, addedStickyKeys, stickyKeys);
                }
                c.body = ob.build();
                foo.add(c);
            }
        }

        List<Case> ret = new ArrayList<>(foo.size());
        Set<Set<String>> bar = new HashSet<>();
        for (Case c : foo) {
            var keySet = c.body.keySet();
            if (bar.contains(keySet)) {
                continue;
            }
            bar.add(keySet);
            ret.add(c);
        }
        return ret;
    }

    private JSON.Instance requestApi(HttpMethod method, String uri) {
        return requestApi(method, uri, null);
    }

    int postCnt = 0;
    int putCnt = 0;

    private JSON.Instance requestApi(HttpMethod method, String uri, JSON.Object body) {
        if (method == HttpMethod.POST) {
            ++postCnt;
        } else if (method == HttpMethod.PUT) {
            ++putCnt;
        }

        Logger.lowLevelDebug("api http request is " + method + " " + uri + " " + body);
        HttpResponse<Buffer> resp = block(f -> {
            HttpRequest<Buffer> req = webClient.request(method, vproxyHTTPPort, "127.0.0.1", "/api/v1/module" + uri);
            if (body != null) {
                req.sendBuffer(Buffer.buffer(body.stringify()), f);
            } else {
                req.send(f);
            }
        });
        Logger.lowLevelDebug("api http response is " + resp.statusCode() + " " + resp.bodyAsString());
        assertTrue(resp.bodyAsString(), 200 == resp.statusCode() || 204 == resp.statusCode());
        String respContent = resp.bodyAsString();
        JSON.Instance ret;
        if (respContent == null) {
            ret = null;
        } else {
            ret = JSON.parse(respContent);
        }
        return ret;
    }

    private void run(String uriBase, Class<?> entityType, Object... _params) throws Exception {
        run0(uriBase, entityType, true, _params);
    }

    private void runNoUpdate(String uriBase, Class<?> entityType, Object... _params) throws Exception {
        run0(uriBase, entityType, false, _params);
    }

    private void run0(String uriBase, Class<?> entityType, boolean needUpdate, Object... _params) throws Exception {
        List<Case> createCases;
        List<Case> updateCases;

        {
            Map<String, Object> params = new HashMap<>();
            for (int i = 0; i < _params.length; i += 2) {
                params.put((String) _params[i], _params[i + 1]);
            }
            Field[] fields = entityType.getDeclaredFields();
            createCases = buildCreateList(fields, params);
            updateCases = buildUpdateList(fields, params);
        }

        // list
        var initialArray = (JSON.Array) requestApi(HttpMethod.GET, uriBase);
        // create
        for (Case c : createCases) {
            Logger.lowLevelDebug("create case: " + c.body);
            final String name = c.body.getString("name");

            var ret = requestApi(HttpMethod.POST, uriBase, c.body);
            assertNull(ret);
            // list again
            {
                var arrayAfterCreate = (JSON.Array) requestApi(HttpMethod.GET, uriBase);
                assertEquals(initialArray.length() + 1, arrayAfterCreate.length());
                {
                    boolean found = false;
                    for (int i = 0; i < arrayAfterCreate.length(); ++i) {
                        if (((JSON.Object) arrayAfterCreate.get(i)).getString("name").equals(name)) {
                            found = true;
                            break;
                        }
                    }
                    assertTrue(found);
                }
            }
            // get
            var item = (JSON.Object) requestApi(HttpMethod.GET, uriBase + "/" + name);
            assertNotNull(item);
            for (String key : c.body.keySet()) {
                assertEquals("value of key " + key + " mismatch", c.body.get(key), item.get(key));
            }
            if (needUpdate) {
                // (update+get) * n
                for (Case uc : updateCases) {
                    Logger.lowLevelDebug("update case: " + uc.body);
                    // update
                    ret = requestApi(HttpMethod.PUT, uriBase + "/" + name, uc.body);
                    assertNull(ret);
                    // get
                    item = (JSON.Object) requestApi(HttpMethod.GET, uriBase + "/" + name);
                    assertNotNull(item);
                    for (String key : uc.body.keySet()) {
                        assertEquals("value of key " + key + " mismatch", uc.body.get(key), item.get(key));
                    }
                }
            }
            // delete
            ret = requestApi(HttpMethod.DELETE, uriBase + "/" + name);
            assertNull(ret);
            // list
            {
                var arrayAfterDelete = (JSON.Array) requestApi(HttpMethod.GET, uriBase);
                assertEquals(initialArray.length(), arrayAfterDelete.length());
                {
                    boolean found = false;
                    for (int i = 0; i < arrayAfterDelete.length(); ++i) {
                        if (((JSON.Object) arrayAfterDelete.get(i)).getString("name").equals(name)) {
                            found = true;
                            break;
                        }
                    }
                    assertFalse(found);
                }
            }
        }
    }

    private String randomUpstream() {
        String n = randomName("ups");
        execute(createReq(add, "upstream", n));
        return n;
    }

    private String randomEventLoopGroup() {
        String n = randomName("elg");
        execute(createReq(add, "event-loop-group", n));
        elgNames.add(n);
        return n;
    }

    private String randomSecurityGroup() {
        String n = randomName("secg");
        execute(createReq(add, "security-group", n, "default", "allow"));
        securgNames.add(n);
        return n;
    }

    private String randomServerGroup() {
        String n = randomName("sg");
        execute(createReq(add, "server-group", n, "timeout", "1000", "period", "1000", "up", "2", "down", "3"));
        sgNames.add(n);
        return n;
    }

    private String randomCertKey() throws Exception {
        var tup = addCertKeyFiles();
        String n = randomName("ck");
        execute(createReq(add, "cert-key", n, "cert", tup.left, "key", tup.right));
        certKeyNames.add(n);
        return n;
    }

    private static int factorial(int n) {
        int ret = 1;
        while (n > 1) {
            ret *= n;
            --n;
        }
        return ret;
    }

    private static int C(int n, int r) {
        return factorial(n) / (factorial(n - r) * factorial(r));
    }

    private static int CC(int n) {
        final int m = n;
        int ret = 0;
        while (n >= 0) {
            ret += C(m, n);
            --n;
        }
        return ret;
    }

    @Test
    public void apiV1TcpLB() throws Exception {
        run("/tcp-lb", Entities.TcpLB.class,
            "backend", randomUpstream(),
            "acceptorLoopGroup", randomEventLoopGroup(),
            "workerLoopGroup", randomEventLoopGroup(),
            "listOfCertKey", new String[]{randomCertKey()},
            "securityGroup", randomSecurityGroup());
        assertEquals(CC(7), postCnt);
        assertEquals(CC(7) * CC(3), putCnt);
    }

    @Test
    public void apiV1TcpLBWithTLS() throws Exception {
        run("/tcp-lb", Entities.TcpLBWithTLS.class,
            "backend", randomUpstream(),
            "acceptorLoopGroup", randomEventLoopGroup(),
            "workerLoopGroup", randomEventLoopGroup(),
            "listOfCertKey", new String[]{randomCertKey()},
            "securityGroup", randomSecurityGroup());
        assertEquals(CC(6), postCnt);
        assertEquals(CC(6) * CC(4), putCnt);
    }

    @Test
    public void apiV1Socks5Server() throws Exception {
        run("/socks5-server", Entities.Socks5Server.class,
            "backend", randomUpstream(),
            "acceptorLoopGroup", randomEventLoopGroup(),
            "workerLoopGroup", randomEventLoopGroup(),
            "securityGroup", randomSecurityGroup());
        assertEquals(CC(6), postCnt);
        assertEquals(CC(6) * CC(4), putCnt);
    }

    @Test
    public void apiV1DNSServer() throws Exception {
        run("/dns-server", Entities.DNSServer.class,
            "eventLoopGroup", randomEventLoopGroup(),
            "rrsets", randomUpstream(),
            "securityGroup", randomSecurityGroup());
        assertEquals(CC(3), postCnt);
        assertEquals(CC(3) * CC(2), putCnt);
    }

    @Test
    public void apiV1EventLoop() throws Exception {
        var elg = randomEventLoopGroup();
        runNoUpdate("/event-loop-group/" + elg + "/event-loop", Entities.EventLoop.class);
        assertEquals(1, postCnt);
        assertEquals(0, putCnt);
    }

    @Test
    public void apiV1ServerGroupInUpstream() throws Exception {
        var ups = randomUpstream();
        var sg = randomServerGroup();
        run("/upstream/" + ups + "/server-group", Entities.ServerGroupInUpstream.class,
            "name", sg);
        assertEquals(CC(2), postCnt);
        assertEquals(CC(2) * CC(2), putCnt);
    }

    @Test
    public void apiV1Upstream() throws Exception {
        runNoUpdate("/upstream", Entities.Upstream.class);
        assertEquals(1, postCnt);
        assertEquals(0, putCnt);
    }

    @Test
    public void apiV1Server() throws Exception {
        var sg = randomServerGroup();
        run("/server-group/" + sg + "/server", Entities.Server.class);
        assertEquals(CC(1), postCnt);
        assertEquals(CC(1) * CC(1), putCnt);
    }

    @Test
    public void apiV1ServerGroup() throws Exception {
        run("/server-group", Entities.ServerGroup.class,
            "protocol", "tcp",
            "eventLoopGroup", randomEventLoopGroup());
        assertEquals(CC(3), postCnt);
        assertEquals(CC(3) * CC(3), putCnt);
    }

    @Test
    public void apiV1ServerGroupNoProtocol() throws Exception {
        run("/server-group", Entities.ServerGroupNoProtocol.class,
            "eventLoopGroup", randomEventLoopGroup());
        assertEquals(CC(3), postCnt);
        assertEquals(CC(3) * CC(3), putCnt);
    }

    @Test
    public void apiV1SecurityGroupRule() throws Exception {
        var secg = randomSecurityGroup();
        runNoUpdate("/security-group/" + secg + "/security-group-rule", Entities.SecurityGroupRule.class,
            "clientNetwork", "192.168.0.0/24",
            "serverPortMin", 0);
        assertEquals(1, postCnt);
        assertEquals(0, putCnt);
    }

    @Test
    public void apiV1SecurityGroup() throws Exception {
        run("/security-group", Entities.SecurityGroup.class);
        assertEquals(1, postCnt);
        assertEquals(1 * CC(1), putCnt);
    }

    private Tuple<String, String> addCertKeyFiles() throws Exception {
        File tmpCertFile = File.createTempFile("cert", ".pem");
        tmpCertFile.deleteOnExit();
        File tmpKeyFile = File.createTempFile("key", ".pem");
        tmpKeyFile.deleteOnExit();

        FileOutputStream fos = new FileOutputStream(tmpCertFile);
        fos.write(TestSSL.TEST_CERT.getBytes());
        fos.flush();
        fos.close();
        fos = new FileOutputStream(tmpKeyFile);
        fos.write(TestSSL.TEST_KEY.getBytes());
        fos.flush();
        fos.close();

        return new Tuple<>(tmpCertFile.getAbsolutePath(), tmpKeyFile.getAbsolutePath());
    }

    @Test
    public void apiV1CertKey() throws Exception {
        var tup = addCertKeyFiles();

        runNoUpdate("/cert-key", Entities.CertKey.class,
            "certs", new String[]{tup.left},
            "key", tup.right);
        assertEquals(1, postCnt);
        assertEquals(0, putCnt);
    }

    @Test
    public void apiV1detail() throws Exception {
        var aelg = randomName("aelg");
        execute(createReq(add, "event-loop-group", aelg));
        elgNames.add(aelg);
        var elg = randomName("elg");
        execute(createReq(add, "event-loop-group", elg));
        elgNames.add(elg);
        var ael0 = randomName("ael0");
        execute(createReq(add, "event-loop", ael0, "to", "event-loop-group", aelg));
        var el0 = randomName("el0");
        var el1 = randomName("el1");
        execute(createReq(add, "event-loop", el0, "to", "event-loop-group", elg));
        execute(createReq(add, "event-loop", el1, "to", "event-loop-group", elg));
        var ups = randomName("ups");
        execute(createReq(add, "upstream", ups));
        upsNames.add(ups);
        var sg0 = randomName("sg0");
        var sg1 = randomName("sg1");
        execute(createReq(add, "server-group", sg0, "timeout", "1000", "period", "5000", "up", "2", "down", "3", "method", "wlc", "event-loop-group", elg, "annotations", "{\"x\":\"y\"}"));
        execute(createReq(add, "server-group", sg1, "timeout", "2000", "period", "2500", "up", "3", "down", "4", "method", "wrr", "event-loop-group", elg, "annotations", "{\"a\":\"b\"}"));
        sgNames.add(sg0);
        sgNames.add(sg1);
        execute(createReq(add, "server-group", sg0, "to", "upstream", ups, "weight", "10", "annotations", "{\"xx\":\"yy\"}"));
        execute(createReq(add, "server-group", sg1, "to", "upstream", ups, "weight", "20", "annotations", "{\"aa\":\"bb\"}"));
        var svr00 = randomName("svr00");
        var svr01 = randomName("svr01");
        execute(createReq(add, "server", svr00, "to", "server-group", sg0, "address", "127.0.0.1:8080", "weight", "10"));
        execute(createReq(add, "server", svr01, "to", "server-group", sg0, "address", "127.0.0.1:8081", "weight", "10"));
        var svr10 = randomName("svr10");
        var svr11 = randomName("svr11");
        execute(createReq(add, "server", svr10, "to", "server-group", sg1, "address", "127.0.0.1:8082", "weight", "10"));
        execute(createReq(add, "server", svr11, "to", "server-group", sg1, "address", "127.0.0.1:8083", "weight", "10"));
        var secg = randomName("secg");
        execute(createReq(add, "security-group", secg, "default", "deny"));
        securgNames.add(secg);
        var secgr0 = randomName("secgr-22");
        var secgr1 = randomName("secgr-80");
        execute(createReq(add, "security-group-rule", secgr0, "to", "security-group", secg,
            "network", "127.0.0.1/32", "protocol", "TCP", "port-range", 22 + "," + 22, "default", "allow"));
        execute(createReq(add, "security-group-rule", secgr1, "to", "security-group", secg,
            "network", "127.0.0.1/32", "protocol", "TCP", "port-range", 80 + "," + 80, "default", "allow"));
        var ck = randomName("ck");
        var ckTup = addCertKeyFiles();
        execute(createReq(add, "cert-key", ck, "cert", ckTup.left, "key", ckTup.right));
        certKeyNames.add(ck);
        var tl = randomName("tl");
        execute(createReq(add, "tcp-lb", tl, "address", "0.0.0.0:18080", "upstream", ups,
            "protocol", "http",
            "acceptor-elg", aelg, "event-loop-group", elg,
            "in-buffer-size", "32768", "out-buffer-size", "32768",
            "cert-key", ck,
            "security-group", secg));
        tlNames.add(tl);
        var socks5 = randomName("socks5");
        execute(createReq(add, "socks5-server", socks5, "address", "0.0.0.0:18081", "upstream", ups,
            "acceptor-elg", aelg, "event-loop-group", elg,
            "in-buffer-size", "32768", "out-buffer-size", "32768",
            "security-group", secg,
            "allow-non-backend"));
        socks5Names.add(socks5);
        var dns = randomName("dns");
        execute(createReq(add, "dns-server", dns, "address", "0.0.0.0:11153", "upstream", ups, "event-loop-group", elg, "ttl", "123"));
        dnsNames.add(dns);

        var tlResp = "{\n" +
            "    \"name\": \"" + tl + "\",\n" +
            "    \"address\": \"0.0.0.0:18080\",\n" +
            "    \"protocol\": \"http\",\n" +
            "    \"backend\": {\n" +
            "        \"name\": \"" + ups + "\",\n" +
            "        \"serverGroupList\": [\n" +
            "            {\n" +
            "                \"name\": \"" + sg0 + "\",\n" +
            "                \"weight\": 10,\n" +
            "                \"annotations\": { \"xx\": \"yy\" },\n" +
            "                \"serverGroup\": {\n" +
            "                    \"name\": \"" + sg0 + "\",\n" +
            "                    \"timeout\": 1000,\n" +
            "                    \"period\": 5000,\n" +
            "                    \"up\": 2,\n" +
            "                    \"down\": 3,\n" +
            "                    \"protocol\": \"tcp\",\n" +
            "                    \"method\": \"wlc\",\n" +
            "                    \"annotations\": { \"x\": \"y\" },\n" +
            "                    \"eventLoopGroup\": {\n" +
            "                        \"name\": \"" + elg + "\",\n" +
            "                        \"eventLoopList\": [\n" +
            "                            { \"name\": \"" + el0 + "\" },\n" +
            "                            { \"name\": \"" + el1 + "\" }\n" +
            "                        ]\n" +
            "                    },\n" +
            "                    \"serverList\": [\n" +
            "                        {\n" +
            "                            \"name\": \"" + svr00 + "\",\n" +
            "                            \"address\": \"127.0.0.1:8080\",\n" +
            "                            \"weight\": 10,\n" +
            "                            \"currentIp\": \"127.0.0.1\",\n" +
            "                            \"status\": \"DOWN\",\n" +
            "                            \"cost\": -1,\n" +
            "                            \"downReason\": \"ConnectException: Connection refused\"\n" +
            "                        },\n" +
            "                        {\n" +
            "                            \"name\": \"" + svr01 + "\",\n" +
            "                            \"address\": \"127.0.0.1:8081\",\n" +
            "                            \"weight\": 10,\n" +
            "                            \"currentIp\": \"127.0.0.1\",\n" +
            "                            \"status\": \"DOWN\",\n" +
            "                            \"cost\": -1," +
            "                            \"downReason\": \"ConnectException: Connection refused\"\n" +
            "                        }\n" +
            "                    ]\n" +
            "                }\n" +
            "            },\n" +
            "            {\n" +
            "                \"name\": \"" + sg1 + "\",\n" +
            "                \"weight\": 20,\n" +
            "                \"annotations\": { \"aa\": \"bb\" },\n" +
            "                \"serverGroup\": {\n" +
            "                    \"name\": \"" + sg1 + "\",\n" +
            "                    \"timeout\": 2000,\n" +
            "                    \"period\": 2500,\n" +
            "                    \"up\": 3,\n" +
            "                    \"down\": 4,\n" +
            "                    \"protocol\": \"tcp\",\n" +
            "                    \"method\": \"wrr\",\n" +
            "                    \"annotations\": { \"a\": \"b\" },\n" +
            "                    \"eventLoopGroup\": {\n" +
            "                        \"name\": \"" + elg + "\",\n" +
            "                        \"eventLoopList\": [\n" +
            "                            { \"name\": \"" + el0 + "\" },\n" +
            "                            { \"name\": \"" + el1 + "\" }\n" +
            "                        ]\n" +
            "                    },\n" +
            "                    \"serverList\": [\n" +
            "                        {\n" +
            "                            \"name\": \"" + svr10 + "\",\n" +
            "                            \"address\": \"127.0.0.1:8082\",\n" +
            "                            \"weight\": 10,\n" +
            "                            \"currentIp\": \"127.0.0.1\",\n" +
            "                            \"status\": \"DOWN\",\n" +
            "                            \"cost\": -1," +
            "                            \"downReason\": \"ConnectException: Connection refused\"\n" +
            "                        },\n" +
            "                        {\n" +
            "                            \"name\": \"" + svr11 + "\",\n" +
            "                            \"address\": \"127.0.0.1:8083\",\n" +
            "                            \"weight\": 10,\n" +
            "                            \"currentIp\": \"127.0.0.1\",\n" +
            "                            \"status\": \"DOWN\",\n" +
            "                            \"cost\": -1," +
            "                            \"downReason\": \"ConnectException: Connection refused\"\n" +
            "                        }\n" +
            "                    ]\n" +
            "                }\n" +
            "            }\n" +
            "        ]\n" +
            "    }," +
            "    \"acceptorLoopGroup\": {\n" +
            "        \"name\": \"" + aelg + "\",\n" +
            "        \"eventLoopList\": [ { \"name\": \"" + ael0 + "\" } ]\n" +
            "    },\n" +
            "    \"workerLoopGroup\": {\n" +
            "        \"name\": \"" + elg + "\",\n" +
            "        \"eventLoopList\": [\n" +
            "            { \"name\": \"" + el0 + "\" },\n" +
            "            { \"name\": \"" + el1 + "\" }\n" +
            "        ]\n" +
            "    },\n" +
            "    \"inBufferSize\": 32768,\n" +
            "    \"outBufferSize\": 32768,\n" +
            "    \"listOfCertKey\": [ {\n" +
            "        \"name\": \"" + ck + "\",\n" +
            "        \"certs\": [ \"" + ckTup.left + "\" ],\n" +
            "        \"certPemList\": [ \"-----BEGIN CERTIFICATE-----\\nMIIDrDCCApQCCQDO2qFtjzwFWzANBgkqhkiG9w0BAQsFADCBljELMAkGA1UEBhMC\\nQ04xETAPBgNVBAgMCFpoZWppYW5nMREwDwYDVQQHDAhIYW5nemhvdTEPMA0GA1UE\\nCgwGdnByb3h5MQ8wDQYDVQQLDAZ2cHJveHkxGzAZBgNVBAMMEnZwcm94eS5jYXNz\\naXRlLm5ldDEiMCAGCSqGSIb3DQEJARYTd2tnY2Fzc0Bob3RtYWlsLmNvbTAgFw0x\\nOTA3MTYwODAxNDNaGA8yMTE5MDYyMjA4MDE0M1owgZYxCzAJBgNVBAYTAkNOMREw\\nDwYDVQQIDAhaaGVqaWFuZzERMA8GA1UEBwwISGFuZ3pob3UxDzANBgNVBAoMBnZw\\ncm94eTEPMA0GA1UECwwGdnByb3h5MRswGQYDVQQDDBJ2cHJveHkuY2Fzc2l0ZS5u\\nZXQxIjAgBgkqhkiG9w0BCQEWE3drZ2Nhc3NAaG90bWFpbC5jb20wggEiMA0GCSqG\\nSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCgTHZBQNzCeuTcN4s5Cc7uKg/iLWwobByG\\nrTTVHAYSpUe0ygaHCAWV4nblf0pSW5uPhhxTGEZFJomjt2EKFkSYEJXpT2C3abQw\\nJw8lZM8gfeqeC/9Xng8c2nffcu8Cy0PcNq1O6B9vXiKQ6JtRHnQeGUIGWWW8cMUT\\nH6FSyk4C/nB64F+bjYeG8bJBNeziUFVBZSeOhE7Pjf42HkotqIpuMzBEhnNWlpY3\\npkgbCZaMNkaeCAW63XveDxj2YaFByLAAoAhtLO9mqcX44e7HILg1POL8rIwNy/l8\\nkkoPu1UHyzOS/f6WaddBjZqtjjls4Ph8xD0ZBwfd27TywGZCOaz/AgMBAAEwDQYJ\\nKoZIhvcNAQELBQADggEBAEC+cvEiSrnZQZRQG+vS4VGnpnerllxfUQxn+JU+B529\\nfJWlacY1TlVxkrAN/33m0xoK5KhyN0ML/OPGcCGQbh36QjZGnFREsDn+xMvs8Kfh\\nufW67kDNh0GTJWHseAI/MXzwVUrfOrEHGEhYat4QjVNtrqQVtsR18f+z+k3pfTED\\ne1C8zyKbbjeCNybOuuGOxc2HHuBFZveDpB3sCyUIW2iS1tCBXvI9u2cLo/QsjsAz\\nkkv6/Fh8BOQT3IMHTh31tfdDJuA0lCs9o9Kc66AaZxTYm8SyNh5L1doYHXoptphI\\ngAAa3BEO21XanlNRU1927oxt6mwNp+WeU1xvyoxCWeE=\\n-----END CERTIFICATE-----\" ],\n" +
            "        \"key\": \"" + ckTup.right + "\",\n" +
            "        \"keySHA1\": \"5K+BFxVJBzNFmjds1fSab7gyI9k=\"\n" +
            "    } ],\n" +
            "    \"securityGroup\": {\n" +
            "        \"name\": \"" + secg + "\",\n" +
            "        \"defaultRule\": \"deny\",\n" +
            "        \"ruleList\": [\n" +
            "            {\n" +
            "                \"name\": \"" + secgr0 + "\",\n" +
            "                \"clientNetwork\": \"127.0.0.1/32\",\n" +
            "                \"protocol\": \"TCP\",\n" +
            "                \"serverPortMin\": 22,\n" +
            "                \"serverPortMax\": 22,\n" +
            "                \"rule\": \"allow\"\n" +
            "            },\n" +
            "            {\n" +
            "                \"name\": \"" + secgr1 + "\",\n" +
            "                \"clientNetwork\": \"127.0.0.1/32\",\n" +
            "                \"protocol\": \"TCP\",\n" +
            "                \"serverPortMin\": 80,\n" +
            "                \"serverPortMax\": 80,\n" +
            "                \"rule\": \"allow\"\n" +
            "            }\n" +
            "        ]\n" +
            "    }\n" +
            "}";
        String pretty;
        pretty = requestApi(HttpMethod.GET, "/tcp-lb/" + tl + "/detail").pretty();
        System.out.println("tcp-lb: " + pretty);
        assertEquals(JSON.parse(tlResp).pretty(), pretty);

        var socks5Resp = "{\n" +
            "    \"name\": \"" + socks5 + "\",\n" +
            "    \"address\": \"0.0.0.0:18081\",\n" +
            "    \"backend\": {\n" +
            "        \"name\": \"" + ups + "\",\n" +
            "        \"serverGroupList\": [\n" +
            "            {\n" +
            "                \"name\": \"" + sg0 + "\",\n" +
            "                \"weight\": 10,\n" +
            "                \"annotations\": { \"xx\": \"yy\" },\n" +
            "                \"serverGroup\": {\n" +
            "                    \"name\": \"" + sg0 + "\",\n" +
            "                    \"timeout\": 1000,\n" +
            "                    \"period\": 5000,\n" +
            "                    \"up\": 2,\n" +
            "                    \"down\": 3,\n" +
            "                    \"protocol\": \"tcp\",\n" +
            "                    \"method\": \"wlc\",\n" +
            "                    \"annotations\": { \"x\": \"y\" },\n" +
            "                    \"eventLoopGroup\": {\n" +
            "                        \"name\": \"" + elg + "\",\n" +
            "                        \"eventLoopList\": [\n" +
            "                            { \"name\": \"" + el0 + "\" },\n" +
            "                            { \"name\": \"" + el1 + "\" }\n" +
            "                        ]\n" +
            "                    },\n" +
            "                    \"serverList\": [\n" +
            "                        {\n" +
            "                            \"name\": \"" + svr00 + "\",\n" +
            "                            \"address\": \"127.0.0.1:8080\",\n" +
            "                            \"weight\": 10,\n" +
            "                            \"currentIp\": \"127.0.0.1\",\n" +
            "                            \"status\": \"DOWN\",\n" +
            "                            \"cost\": -1," +
            "                            \"downReason\": \"ConnectException: Connection refused\"\n" +
            "                        },\n" +
            "                        {\n" +
            "                            \"name\": \"" + svr01 + "\",\n" +
            "                            \"address\": \"127.0.0.1:8081\",\n" +
            "                            \"weight\": 10,\n" +
            "                            \"currentIp\": \"127.0.0.1\",\n" +
            "                            \"status\": \"DOWN\",\n" +
            "                            \"cost\": -1," +
            "                            \"downReason\": \"ConnectException: Connection refused\"\n" +
            "                        }\n" +
            "                    ]\n" +
            "                }\n" +
            "            },\n" +
            "            {\n" +
            "                \"name\": \"" + sg1 + "\",\n" +
            "                \"weight\": 20,\n" +
            "                \"annotations\": { \"aa\": \"bb\" },\n" +
            "                \"serverGroup\": {\n" +
            "                    \"name\": \"" + sg1 + "\",\n" +
            "                    \"timeout\": 2000,\n" +
            "                    \"period\": 2500,\n" +
            "                    \"up\": 3,\n" +
            "                    \"down\": 4,\n" +
            "                    \"protocol\": \"tcp\",\n" +
            "                    \"method\": \"wrr\",\n" +
            "                    \"annotations\": { \"a\": \"b\" },\n" +
            "                    \"eventLoopGroup\": {\n" +
            "                        \"name\": \"" + elg + "\",\n" +
            "                        \"eventLoopList\": [\n" +
            "                            { \"name\": \"" + el0 + "\" },\n" +
            "                            { \"name\": \"" + el1 + "\" }\n" +
            "                        ]\n" +
            "                    },\n" +
            "                    \"serverList\": [\n" +
            "                        {\n" +
            "                            \"name\": \"" + svr10 + "\",\n" +
            "                            \"address\": \"127.0.0.1:8082\",\n" +
            "                            \"weight\": 10,\n" +
            "                            \"currentIp\": \"127.0.0.1\",\n" +
            "                            \"status\": \"DOWN\",\n" +
            "                            \"cost\": -1," +
            "                            \"downReason\": \"ConnectException: Connection refused\"\n" +
            "                        },\n" +
            "                        {\n" +
            "                            \"name\": \"" + svr11 + "\",\n" +
            "                            \"address\": \"127.0.0.1:8083\",\n" +
            "                            \"weight\": 10,\n" +
            "                            \"currentIp\": \"127.0.0.1\",\n" +
            "                            \"status\": \"DOWN\",\n" +
            "                            \"cost\": -1," +
            "                            \"downReason\": \"ConnectException: Connection refused\"\n" +
            "                        }\n" +
            "                    ]\n" +
            "                }\n" +
            "            }\n" +
            "        ]\n" +
            "    }," +
            "    \"acceptorLoopGroup\": {\n" +
            "        \"name\": \"" + aelg + "\",\n" +
            "        \"eventLoopList\": [ { \"name\": \"" + ael0 + "\" } ]\n" +
            "    },\n" +
            "    \"workerLoopGroup\": {\n" +
            "        \"name\": \"" + elg + "\",\n" +
            "        \"eventLoopList\": [\n" +
            "            { \"name\": \"" + el0 + "\" },\n" +
            "            { \"name\": \"" + el1 + "\" }\n" +
            "        ]\n" +
            "    },\n" +
            "    \"inBufferSize\": 32768,\n" +
            "    \"outBufferSize\": 32768,\n" +
            "    \"securityGroup\": {\n" +
            "        \"name\": \"" + secg + "\",\n" +
            "        \"defaultRule\": \"deny\",\n" +
            "        \"ruleList\": [\n" +
            "            {\n" +
            "                \"name\": \"" + secgr0 + "\",\n" +
            "                \"clientNetwork\": \"127.0.0.1/32\",\n" +
            "                \"protocol\": \"TCP\",\n" +
            "                \"serverPortMin\": 22,\n" +
            "                \"serverPortMax\": 22,\n" +
            "                \"rule\": \"allow\"\n" +
            "            },\n" +
            "            {\n" +
            "                \"name\": \"" + secgr1 + "\",\n" +
            "                \"clientNetwork\": \"127.0.0.1/32\",\n" +
            "                \"protocol\": \"TCP\",\n" +
            "                \"serverPortMin\": 80,\n" +
            "                \"serverPortMax\": 80,\n" +
            "                \"rule\": \"allow\"\n" +
            "            }\n" +
            "        ]\n" +
            "    },\n" +
            "    \"allowNonBackend\": true\n" +
            "}";
        pretty = requestApi(HttpMethod.GET, "/socks5-server/" + socks5 + "/detail").pretty();
        System.out.println("socks5-server: " + pretty);
        assertEquals(JSON.parse(socks5Resp).pretty(), pretty);

        var dnsResp = "{\n" +
            "    \"name\": \"" + dns + "\",\n" +
            "    \"address\": \"0.0.0.0:11153\",\n" +
            "    \"ttl\": 123,\n" +
            "    \"rrsets\": {\n" +
            "        \"name\": \"" + ups + "\",\n" +
            "        \"serverGroupList\": [\n" +
            "            {\n" +
            "                \"name\": \"" + sg0 + "\",\n" +
            "                \"weight\": 10,\n" +
            "                \"annotations\": { \"xx\": \"yy\" },\n" +
            "                \"serverGroup\": {\n" +
            "                    \"name\": \"" + sg0 + "\",\n" +
            "                    \"timeout\": 1000,\n" +
            "                    \"period\": 5000,\n" +
            "                    \"up\": 2,\n" +
            "                    \"down\": 3,\n" +
            "                    \"protocol\": \"tcp\",\n" +
            "                    \"method\": \"wlc\",\n" +
            "                    \"annotations\": { \"x\": \"y\" },\n" +
            "                    \"eventLoopGroup\": {\n" +
            "                        \"name\": \"" + elg + "\",\n" +
            "                        \"eventLoopList\": [\n" +
            "                            { \"name\": \"" + el0 + "\" },\n" +
            "                            { \"name\": \"" + el1 + "\" }\n" +
            "                        ]\n" +
            "                    },\n" +
            "                    \"serverList\": [\n" +
            "                        {\n" +
            "                            \"name\": \"" + svr00 + "\",\n" +
            "                            \"address\": \"127.0.0.1:8080\",\n" +
            "                            \"weight\": 10,\n" +
            "                            \"currentIp\": \"127.0.0.1\",\n" +
            "                            \"status\": \"DOWN\",\n" +
            "                            \"cost\": -1," +
            "                            \"downReason\": \"ConnectException: Connection refused\"\n" +
            "                        },\n" +
            "                        {\n" +
            "                            \"name\": \"" + svr01 + "\",\n" +
            "                            \"address\": \"127.0.0.1:8081\",\n" +
            "                            \"weight\": 10,\n" +
            "                            \"currentIp\": \"127.0.0.1\",\n" +
            "                            \"status\": \"DOWN\",\n" +
            "                            \"cost\": -1," +
            "                            \"downReason\": \"ConnectException: Connection refused\"\n" +
            "                        }\n" +
            "                    ]\n" +
            "                }\n" +
            "            },\n" +
            "            {\n" +
            "                \"name\": \"" + sg1 + "\",\n" +
            "                \"weight\": 20,\n" +
            "                \"annotations\": { \"aa\": \"bb\" },\n" +
            "                \"serverGroup\": {\n" +
            "                    \"name\": \"" + sg1 + "\",\n" +
            "                    \"timeout\": 2000,\n" +
            "                    \"period\": 2500,\n" +
            "                    \"up\": 3,\n" +
            "                    \"down\": 4,\n" +
            "                    \"protocol\": \"tcp\",\n" +
            "                    \"method\": \"wrr\",\n" +
            "                    \"annotations\": { \"a\": \"b\" },\n" +
            "                    \"eventLoopGroup\": {\n" +
            "                        \"name\": \"" + elg + "\",\n" +
            "                        \"eventLoopList\": [\n" +
            "                            { \"name\": \"" + el0 + "\" },\n" +
            "                            { \"name\": \"" + el1 + "\" }\n" +
            "                        ]\n" +
            "                    },\n" +
            "                    \"serverList\": [\n" +
            "                        {\n" +
            "                            \"name\": \"" + svr10 + "\",\n" +
            "                            \"address\": \"127.0.0.1:8082\",\n" +
            "                            \"weight\": 10,\n" +
            "                            \"currentIp\": \"127.0.0.1\",\n" +
            "                            \"status\": \"DOWN\",\n" +
            "                            \"cost\": -1," +
            "                            \"downReason\": \"ConnectException: Connection refused\"\n" +
            "                        },\n" +
            "                        {\n" +
            "                            \"name\": \"" + svr11 + "\",\n" +
            "                            \"address\": \"127.0.0.1:8083\",\n" +
            "                            \"weight\": 10,\n" +
            "                            \"currentIp\": \"127.0.0.1\",\n" +
            "                            \"status\": \"DOWN\",\n" +
            "                            \"cost\": -1," +
            "                            \"downReason\": \"ConnectException: Connection refused\"\n" +
            "                        }\n" +
            "                    ]\n" +
            "                }\n" +
            "            }\n" +
            "        ]\n" +
            "    },\n" +
            "    \"eventLoopGroup\": {\n" +
            "        \"name\": \"" + elg + "\",\n" +
            "        \"eventLoopList\": [\n" +
            "            { \"name\": \"" + el0 + "\" },\n" +
            "            { \"name\": \"" + el1 + "\" }\n" +
            "        ]\n" +
            "    },\n" +
            "    \"securityGroup\": {\n" +
            "        \"name\": \"(allow-all)\",\n" +
            "        \"defaultRule\": \"allow\",\n" +
            "        \"ruleList\": []\n" +
            "    }\n" +
            "}";
        pretty = requestApi(HttpMethod.GET, "/dns-server/" + dns + "/detail").pretty();
        System.out.println("dns:" + pretty);
        assertEquals(JSON.parse(dnsResp).pretty(), pretty);

        var elResp = "{ \"name\": \"" + el0 + "\" }";
        pretty = requestApi(HttpMethod.GET, "/event-loop-group/" + elg + "/event-loop/" + el0 + "/detail").pretty();
        System.out.println("event-loop: " + pretty);
        assertEquals(JSON.parse(elResp).pretty(), pretty);

        var elgResp = "{\n" +
            "    \"name\": \"" + elg + "\",\n" +
            "    \"eventLoopList\": [\n" +
            "        { \"name\": \"" + el0 + "\" },\n" +
            "        { \"name\": \"" + el1 + "\" }\n" +
            "    ]\n" +
            "}";
        pretty = requestApi(HttpMethod.GET, "/event-loop-group/" + elg + "/detail").pretty();
        System.out.println("event-loop-group: " + pretty);
        assertEquals(JSON.parse(elgResp).pretty(), pretty);

        var sgInSgsResp = "{\n" +
            "    \"name\": \"" + sg0 + "\",\n" +
            "    \"weight\": 10,\n" +
            "    \"annotations\": { \"xx\": \"yy\" },\n" +
            "    \"serverGroup\": {\n" +
            "        \"name\": \"" + sg0 + "\",\n" +
            "        \"timeout\": 1000,\n" +
            "        \"period\": 5000,\n" +
            "        \"up\": 2,\n" +
            "        \"down\": 3,\n" +
            "        \"protocol\": \"tcp\",\n" +
            "        \"method\": \"wlc\",\n" +
            "        \"annotations\": { \"x\": \"y\" },\n" +
            "        \"eventLoopGroup\": {\n" +
            "            \"name\": \"" + elg + "\",\n" +
            "            \"eventLoopList\": [\n" +
            "                { \"name\": \"" + el0 + "\" },\n" +
            "                { \"name\": \"" + el1 + "\" }\n" +
            "            ]\n" +
            "        },\n" +
            "        \"serverList\": [\n" +
            "            {\n" +
            "                \"name\": \"" + svr00 + "\",\n" +
            "                \"address\": \"127.0.0.1:8080\",\n" +
            "                \"weight\": 10,\n" +
            "                \"currentIp\": \"127.0.0.1\",\n" +
            "                \"status\": \"DOWN\",\n" +
            "                \"cost\": -1," +
            "                \"downReason\": \"ConnectException: Connection refused\"\n" +
            "            },\n" +
            "            {\n" +
            "                \"name\": \"" + svr01 + "\",\n" +
            "                \"address\": \"127.0.0.1:8081\",\n" +
            "                \"weight\": 10,\n" +
            "                \"currentIp\": \"127.0.0.1\",\n" +
            "                \"status\": \"DOWN\",\n" +
            "                \"cost\": -1," +
            "                \"downReason\": \"ConnectException: Connection refused\"\n" +
            "            }\n" +
            "        ]\n" +
            "    }\n" +
            "}";
        pretty = requestApi(HttpMethod.GET, "/upstream/" + ups + "/server-group/" + sg0 + "/detail").pretty();
        System.out.println("server-group in upstream: " + pretty);
        assertEquals(JSON.parse(sgInSgsResp).pretty(), pretty);

        var upsResp = "{\n" +
            "    \"name\": \"" + ups + "\",\n" +
            "    \"serverGroupList\": [\n" +
            "        {\n" +
            "            \"name\": \"" + sg0 + "\",\n" +
            "            \"weight\": 10,\n" +
            "            \"annotations\": { \"xx\": \"yy\" },\n" +
            "            \"serverGroup\": {\n" +
            "                \"name\": \"" + sg0 + "\",\n" +
            "                \"timeout\": 1000,\n" +
            "                \"period\": 5000,\n" +
            "                \"up\": 2,\n" +
            "                \"down\": 3,\n" +
            "                \"protocol\": \"tcp\",\n" +
            "                \"method\": \"wlc\",\n" +
            "                \"annotations\": { \"x\": \"y\" },\n" +
            "                \"eventLoopGroup\": {\n" +
            "                    \"name\": \"" + elg + "\",\n" +
            "                    \"eventLoopList\": [\n" +
            "                        { \"name\": \"" + el0 + "\" },\n" +
            "                        { \"name\": \"" + el1 + "\" }\n" +
            "                    ]\n" +
            "                },\n" +
            "                \"serverList\": [\n" +
            "                    {\n" +
            "                        \"name\": \"" + svr00 + "\",\n" +
            "                        \"address\": \"127.0.0.1:8080\",\n" +
            "                        \"weight\": 10,\n" +
            "                        \"currentIp\": \"127.0.0.1\",\n" +
            "                        \"status\": \"DOWN\",\n" +
            "                        \"cost\": -1," +
            "                        \"downReason\": \"ConnectException: Connection refused\"\n" +
            "                    },\n" +
            "                    {\n" +
            "                        \"name\": \"" + svr01 + "\",\n" +
            "                        \"address\": \"127.0.0.1:8081\",\n" +
            "                        \"weight\": 10,\n" +
            "                        \"currentIp\": \"127.0.0.1\",\n" +
            "                        \"status\": \"DOWN\",\n" +
            "                        \"cost\": -1," +
            "                        \"downReason\": \"ConnectException: Connection refused\"\n" +
            "                    }\n" +
            "                ]\n" +
            "            }\n" +
            "        },\n" +
            "        {\n" +
            "            \"name\": \"" + sg1 + "\",\n" +
            "            \"weight\": 20,\n" +
            "            \"annotations\": { \"aa\": \"bb\" },\n" +
            "            \"serverGroup\": {\n" +
            "                \"name\": \"" + sg1 + "\",\n" +
            "                \"timeout\": 2000,\n" +
            "                \"period\": 2500,\n" +
            "                \"up\": 3,\n" +
            "                \"down\": 4,\n" +
            "                \"protocol\": \"tcp\",\n" +
            "                \"method\": \"wrr\",\n" +
            "                \"annotations\": { \"a\": \"b\" },\n" +
            "                \"eventLoopGroup\": {\n" +
            "                    \"name\": \"" + elg + "\",\n" +
            "                    \"eventLoopList\": [\n" +
            "                        { \"name\": \"" + el0 + "\" },\n" +
            "                        { \"name\": \"" + el1 + "\" }\n" +
            "                    ]\n" +
            "                },\n" +
            "                \"serverList\": [\n" +
            "                    {\n" +
            "                        \"name\": \"" + svr10 + "\",\n" +
            "                        \"address\": \"127.0.0.1:8082\",\n" +
            "                        \"weight\": 10,\n" +
            "                        \"currentIp\": \"127.0.0.1\",\n" +
            "                        \"status\": \"DOWN\",\n" +
            "                        \"cost\": -1," +
            "                        \"downReason\": \"ConnectException: Connection refused\"\n" +
            "                    },\n" +
            "                    {\n" +
            "                        \"name\": \"" + svr11 + "\",\n" +
            "                        \"address\": \"127.0.0.1:8083\",\n" +
            "                        \"weight\": 10,\n" +
            "                        \"currentIp\": \"127.0.0.1\",\n" +
            "                        \"status\": \"DOWN\",\n" +
            "                        \"cost\": -1," +
            "                        \"downReason\": \"ConnectException: Connection refused\"\n" +
            "                    }\n" +
            "                ]\n" +
            "            }\n" +
            "        }\n" +
            "    ]\n" +
            "}";
        pretty = requestApi(HttpMethod.GET, "/upstream/" + ups + "/detail").pretty();
        System.out.println("upstream: " + pretty);
        assertEquals(JSON.parse(upsResp).pretty(), pretty);

        var serverResp = "{\n" +
            "    \"name\": \"" + svr00 + "\",\n" +
            "    \"address\": \"127.0.0.1:8080\",\n" +
            "    \"weight\": 10,\n" +
            "    \"currentIp\": \"127.0.0.1\",\n" +
            "    \"status\": \"DOWN\",\n" +
            "    \"cost\": -1," +
            "    \"downReason\": \"ConnectException: Connection refused\"\n" +
            "}";
        pretty = requestApi(HttpMethod.GET, "/server-group/" + sg0 + "/server/" + svr00 + "/detail").pretty();
        System.out.println("server: " + pretty);
        assertEquals(JSON.parse(serverResp).pretty(), pretty);

        var sgResp = "{\n" +
            "    \"name\": \"" + sg0 + "\",\n" +
            "    \"timeout\": 1000,\n" +
            "    \"period\": 5000,\n" +
            "    \"up\": 2,\n" +
            "    \"down\": 3,\n" +
            "    \"protocol\": \"tcp\",\n" +
            "    \"method\": \"wlc\",\n" +
            "    \"annotations\": { \"x\": \"y\" },\n" +
            "    \"eventLoopGroup\": {\n" +
            "        \"name\": \"" + elg + "\",\n" +
            "        \"eventLoopList\": [\n" +
            "            { \"name\": \"" + el0 + "\" },\n" +
            "            { \"name\": \"" + el1 + "\" }\n" +
            "        ]\n" +
            "    },\n" +
            "    \"serverList\": [\n" +
            "        {\n" +
            "            \"name\": \"" + svr00 + "\",\n" +
            "            \"address\": \"127.0.0.1:8080\",\n" +
            "            \"weight\": 10,\n" +
            "            \"currentIp\": \"127.0.0.1\",\n" +
            "            \"status\": \"DOWN\",\n" +
            "            \"cost\": -1," +
            "            \"downReason\": \"ConnectException: Connection refused\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"name\": \"" + svr01 + "\",\n" +
            "            \"address\": \"127.0.0.1:8081\",\n" +
            "            \"weight\": 10,\n" +
            "            \"currentIp\": \"127.0.0.1\",\n" +
            "            \"status\": \"DOWN\",\n" +
            "            \"cost\": -1," +
            "            \"downReason\": \"ConnectException: Connection refused\"\n" +
            "        }\n" +
            "    ]\n" +
            "}";
        pretty = requestApi(HttpMethod.GET, "/server-group/" + sg0 + "/detail").pretty();
        System.out.println("server-group: " + pretty);
        assertEquals(JSON.parse(sgResp).pretty(), pretty);

        var secgrResp = "{\n" +
            "    \"name\": \"" + secgr0 + "\",\n" +
            "    \"clientNetwork\": \"127.0.0.1/32\",\n" +
            "    \"protocol\": \"TCP\",\n" +
            "    \"serverPortMin\": 22,\n" +
            "    \"serverPortMax\": 22,\n" +
            "    \"rule\": \"allow\"\n" +
            "}";
        pretty = requestApi(HttpMethod.GET, "/security-group/" + secg + "/security-group-rule/" + secgr0 + "/detail").pretty();
        System.out.println("security-group-rule: " + pretty);
        assertEquals(JSON.parse(secgrResp).pretty(), pretty);

        var secgResp = "{\n" +
            "    \"name\": \"" + secg + "\",\n" +
            "    \"defaultRule\": \"deny\",\n" +
            "    \"ruleList\": [\n" +
            "        {\n" +
            "            \"name\": \"" + secgr0 + "\",\n" +
            "            \"clientNetwork\": \"127.0.0.1/32\",\n" +
            "            \"protocol\": \"TCP\",\n" +
            "            \"serverPortMin\": 22,\n" +
            "            \"serverPortMax\": 22,\n" +
            "            \"rule\": \"allow\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"name\": \"" + secgr1 + "\",\n" +
            "            \"clientNetwork\": \"127.0.0.1/32\",\n" +
            "            \"protocol\": \"TCP\",\n" +
            "            \"serverPortMin\": 80,\n" +
            "            \"serverPortMax\": 80,\n" +
            "            \"rule\": \"allow\"\n" +
            "        }\n" +
            "    ]\n" +
            "}";
        pretty = requestApi(HttpMethod.GET, "/security-group/" + secg + "/detail").pretty();
        System.out.println("security-group: " + pretty);
        assertEquals(JSON.parse(secgResp).pretty(), pretty);

        var ckResp = "{\n" +
            "    \"name\": \"" + ck + "\",\n" +
            "    \"certs\": [ \"" + ckTup.left + "\" ],\n" +
            "    \"certPemList\": [ \"-----BEGIN CERTIFICATE-----\\nMIIDrDCCApQCCQDO2qFtjzwFWzANBgkqhkiG9w0BAQsFADCBljELMAkGA1UEBhMC\\nQ04xETAPBgNVBAgMCFpoZWppYW5nMREwDwYDVQQHDAhIYW5nemhvdTEPMA0GA1UE\\nCgwGdnByb3h5MQ8wDQYDVQQLDAZ2cHJveHkxGzAZBgNVBAMMEnZwcm94eS5jYXNz\\naXRlLm5ldDEiMCAGCSqGSIb3DQEJARYTd2tnY2Fzc0Bob3RtYWlsLmNvbTAgFw0x\\nOTA3MTYwODAxNDNaGA8yMTE5MDYyMjA4MDE0M1owgZYxCzAJBgNVBAYTAkNOMREw\\nDwYDVQQIDAhaaGVqaWFuZzERMA8GA1UEBwwISGFuZ3pob3UxDzANBgNVBAoMBnZw\\ncm94eTEPMA0GA1UECwwGdnByb3h5MRswGQYDVQQDDBJ2cHJveHkuY2Fzc2l0ZS5u\\nZXQxIjAgBgkqhkiG9w0BCQEWE3drZ2Nhc3NAaG90bWFpbC5jb20wggEiMA0GCSqG\\nSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCgTHZBQNzCeuTcN4s5Cc7uKg/iLWwobByG\\nrTTVHAYSpUe0ygaHCAWV4nblf0pSW5uPhhxTGEZFJomjt2EKFkSYEJXpT2C3abQw\\nJw8lZM8gfeqeC/9Xng8c2nffcu8Cy0PcNq1O6B9vXiKQ6JtRHnQeGUIGWWW8cMUT\\nH6FSyk4C/nB64F+bjYeG8bJBNeziUFVBZSeOhE7Pjf42HkotqIpuMzBEhnNWlpY3\\npkgbCZaMNkaeCAW63XveDxj2YaFByLAAoAhtLO9mqcX44e7HILg1POL8rIwNy/l8\\nkkoPu1UHyzOS/f6WaddBjZqtjjls4Ph8xD0ZBwfd27TywGZCOaz/AgMBAAEwDQYJ\\nKoZIhvcNAQELBQADggEBAEC+cvEiSrnZQZRQG+vS4VGnpnerllxfUQxn+JU+B529\\nfJWlacY1TlVxkrAN/33m0xoK5KhyN0ML/OPGcCGQbh36QjZGnFREsDn+xMvs8Kfh\\nufW67kDNh0GTJWHseAI/MXzwVUrfOrEHGEhYat4QjVNtrqQVtsR18f+z+k3pfTED\\ne1C8zyKbbjeCNybOuuGOxc2HHuBFZveDpB3sCyUIW2iS1tCBXvI9u2cLo/QsjsAz\\nkkv6/Fh8BOQT3IMHTh31tfdDJuA0lCs9o9Kc66AaZxTYm8SyNh5L1doYHXoptphI\\ngAAa3BEO21XanlNRU1927oxt6mwNp+WeU1xvyoxCWeE=\\n-----END CERTIFICATE-----\" ],\n" +
            "    \"key\": \"" + ckTup.right + "\",\n" +
            "    \"keySHA1\": \"5K+BFxVJBzNFmjds1fSab7gyI9k=\"\n" +
            "}";
        pretty = requestApi(HttpMethod.GET, "/cert-key/" + ck + "/detail").pretty();
        System.out.println("cert-key: " + pretty);
        assertEquals(JSON.parse(ckResp).pretty(), pretty);
    }
}
