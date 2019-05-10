package net.cassite.vproxy.ci;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.*;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.redis.client.*;
import org.junit.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

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

    @BeforeClass
    public static void setUpClass() {
        String strPort = System.getProperty("vproxy_port");
        if (strPort == null)
            strPort = System.getenv("vproxy_port");
        if (strPort == null)
            strPort = "16379";
        int port = Integer.parseInt(strPort);

        String password = System.getProperty("vproxy_password");
        if (password == null)
            password = System.getenv("vproxy_password");
        if (password == null)
            password = "123456";

        if (System.getProperty("vproxy_exists") == null && System.getenv("vproxy_exists") == null) {
            net.cassite.vproxy.app.Main.main(new String[]{
                "resp-controller", "localhost:" + port, password,
                "allowSystemCallInNonStdIOController",
                "noStdIOController",
                "noLoadLast",
                "noSave"
            });
        }

        vertx = Vertx.vertx();
        redis = Redis.createClient(vertx, new RedisOptions()
            .setEndpoint(SocketAddress.inetSocketAddress(port, "127.0.0.1"))
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
    private List<String> elgNames = new ArrayList<>();
    private List<String> sgsNames = new ArrayList<>();
    private List<String> sgNames = new ArrayList<>();
    private List<String> securgNames = new ArrayList<>();
    private List<String> slgNames = new ArrayList<>();

    private String elg0;
    private String elg1;
    private String sgs0;

    private WebClient socks5WebClient = null;
    private WebClient h2WebClient = null;
    private NetClient netClient = null;

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

        sgs0 = randomName("sgs0");
        execute(createReq(add, "server-groups", sgs0));
        sgsNames.add(sgs0);
        checkCreate("server-groups", sgs0);
    }

    @After
    public void tearDown() {
        // remove one another according to dependency
        // remove smart-lb-group
        for (String slg : slgNames) {
            execute(createReq(remove, "smart-lb-group", slg));
            checkRemove("smart-lb-group", slg);
        }
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
        // remove server groups
        for (String sgs : sgsNames) {
            execute(createReq(remove, "server-groups", sgs));
            checkRemove("server-groups", sgs);
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

        if (socks5WebClient != null) {
            socks5WebClient.close();
        }
        if (h2WebClient != null) {
            h2WebClient.close();
        }
        if (netClient != null) {
            netClient.close();
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

    private void initNetClient() {
        netClient = vertx.createNetClient(new NetClientOptions());
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

    @Test
    public void simpleLB() throws Exception {
        int port = 7001;
        String lbName = randomName("lb0");
        execute(createReq(add, "tcp-lb", lbName,
            "acceptor-elg", elg0, "event-loop-group", elg1,
            "address", "127.0.0.1:" + port,
            "server-groups", sgs0));
        tlNames.add(lbName);
        checkCreate("tcp-lb", lbName);
        Map<String, String> detail = getDetail("tcp-lb", lbName);
        assertEquals(elg0, detail.get("acceptor"));
        assertEquals(elg1, detail.get("worker"));
        assertEquals("127.0.0.1:" + port, detail.get("bind"));
        assertEquals(sgs0, detail.get("backends"));
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

        execute(createReq(add, "server-group", sg0, "to", "server-groups", sgs0, "weight", "10"));
        checkCreate("server-group", sg0, "server-groups", sgs0);

        execute(createReq(add, "server", "sg7771", "to", "server-group", sg0, "address", "127.0.0.1:7771", "weight", "10"));
        checkCreate("server", "sg7771", "server-group", sg0);
        execute(createReq(add, "server", "sg7772", "to", "server-group", sg0, "address", "127.0.0.1:7772", "weight", "10"));
        checkCreate("server", "sg7772", "server-group", sg0);

        Thread.sleep(500);

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
            "server-groups", sgs0));
        socks5Names.add(socks5Name);
        checkCreate("socks5-server", socks5Name);
        Map<String, String> detail = getDetail("socks5-server", socks5Name);
        assertEquals(elg0, detail.get("acceptor"));
        assertEquals(elg1, detail.get("worker"));
        assertEquals("127.0.0.1:" + port, detail.get("bind"));
        assertEquals(sgs0, detail.get("backends"));
        assertEquals("16384", detail.get("in-buffer-size"));
        assertEquals("16384", detail.get("out-buffer-size"));
        assertEquals("(allow-all)", detail.get("security-group"));
        assertEquals("", detail.get("deny-non-backend"));
        assertNull(detail.get("allow-non-backend"));

        String sg0 = "myexample.com:8080";
        execute(createReq(add, "server-group", sg0,
            "timeout", "500", "period", "200", "up", "2", "down", "5",
            "event-loop-group", elg0));
        sgNames.add(sg0);
        checkCreate("server-group", sg0);

        String sg1 = "myexample2.com:8080";
        execute(createReq(add, "server-group", sg1,
            "timeout", "500", "period", "200", "up", "2", "down", "5",
            "event-loop-group", elg0));
        sgNames.add(sg1);
        checkCreate("server-group", sg1);

        execute(createReq(add, "server-group", sg0, "to", "server-groups", sgs0, "weight", "10"));
        checkCreate("server-group", sg0, "server-groups", sgs0);
        execute(createReq(add, "server-group", sg1, "to", "server-groups", sgs0, "weight", "10"));
        checkCreate("server-group", sg1, "server-groups", sgs0);

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
    public void serverGroupMethod() throws Exception {
        int port = 7003;
        String lbName = randomName("lb0");
        execute(createReq(add, "tcp-lb", lbName,
            "acceptor-elg", elg0, "event-loop-group", elg1,
            "address", "127.0.0.1:" + port,
            "server-groups", sgs0));
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
            assertEquals("wrr", details.get("method"));
            assertEquals(elg0, details.get("event-loop-group"));
        }

        execute(createReq(add, "server-group", sg0, "to", "server-groups", sgs0, "weight", "12"));
        checkCreate("server-group", sg0, "server-groups", sgs0);
        {
            Map<String, String> details = getDetail("server-group", sg0, "server-groups", sgs0);
            assertEquals("500", details.get("timeout"));
            assertEquals("200", details.get("period"));
            assertEquals("2", details.get("up"));
            assertEquals("5", details.get("down"));
            assertEquals("wrr", details.get("method"));
            assertEquals(elg0, details.get("event-loop-group"));
            assertEquals("12", details.get("weight"));
        }

        // weight = 2 : 1
        execute(createReq(add, "server", "sg7771", "to", "server-group", sg0, "address", "127.0.0.1:7771", "weight", "10"));
        execute(createReq(add, "server", "sg7772", "to", "server-group", sg0, "address", "127.0.0.1:7772", "weight", "5"));
        checkCreate("server", "sg7771", "server-group", sg0);
        checkCreate("server", "sg7772", "server-group", sg0);

        Thread.sleep(500);

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
            details = getDetail("server-group", sg0, "server-groups", sgs0);
            assertEquals("wlc", details.get("method"));
        }
        // remove server temporarily
        execute(createReq(remove, "server", "sg7772", "from", "server-group", sg0));
        checkRemove("server", "sg7772", "server-group", sg0);

        initNetClient();

        List<NetSocket> socks7771 = new ArrayList<>();
        for (int i = 0; i < 5; ++i) {
            NetSocket sock = block(f -> netClient.connect(port, "127.0.0.1", f));
            socks7771.add(sock);
        }

        // then add the server back
        // set weight to 20
        execute(createReq(add, "server", "sg7772", "to", "server-group", sg0, "address", "127.0.0.1:7772", "weight", "20"));
        checkCreate("server", "sg7772", "server-group", sg0);

        Thread.sleep(500);

        List<NetSocket> socks7772 = new ArrayList<>();
        for (int i = 0; i < 10; ++i) {
            NetSocket sock = block(f -> netClient.connect(port, "127.0.0.1", f));
            socks7772.add(sock);
        }

        for (NetSocket socks : socks7771) {
            socks.write("" +
                "GET / HTTP/1.1\r\n" +
                "Host: 127.0.0.1\r\n" +
                "\r\n");
            String[] result = {null};
            socks.handler(b -> result[0] = b.toString());
            while (result[0] == null) {
                Thread.sleep(1);
            }
            String foo = result[0].trim();
            assertTrue(foo.endsWith("7771"));
        }

        for (NetSocket socks : socks7772) {
            socks.write("" +
                "GET / HTTP/1.1\r\n" +
                "Host: 127.0.0.1\r\n" +
                "\r\n");
            String[] result = {null};
            socks.handler(b -> result[0] = b.toString());
            while (result[0] == null) {
                Thread.sleep(1);
            }
            String foo = result[0].trim();
            assertTrue(foo.endsWith("7772"));
        }

        // update to source
        execute(createReq(update, "server-group", sg0, "method", "source"));
        {
            Map<String, String> details = getDetail("server-group", sg0);
            assertEquals("source", details.get("method"));
            details = getDetail("server-group", sg0, "server-groups", sgs0);
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
            "server-groups", sgs0));
        tlNames.add(lbName);
        checkCreate("tcp-lb", lbName);

        String sg0 = randomName("sg0");
        execute(createReq(add, "server-group", sg0,
            "timeout", "500", "period", "200", "up", "2", "down", "5",
            "event-loop-group", elg0));
        sgNames.add(sg0);
        checkCreate("server-group", sg0);

        execute(createReq(add, "server-group", sg0, "to", "server-groups", sgs0, "weight", "10"));
        checkCreate("server-group", sg0, "server-groups", sgs0);

        String sg1 = randomName("sg1");
        execute(createReq(add, "server-group", sg1,
            "timeout", "500", "period", "200", "up", "2", "down", "5",
            "event-loop-group", elg0));
        sgNames.add(sg1);
        checkCreate("server-group", sg1);

        execute(createReq(add, "server-group", sg1, "to", "server-groups", sgs0, "weight", "5"));
        checkCreate("server-group", sg1, "server-groups", sgs0);

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

        execute(createReq(update, "server-group", sg1, "in", "server-groups", sgs0, "weight", "15"));
        weightCheck(port, new String[]{"7771", "7772", "7773", "7774"}, new int[]{12, 8, 15, 15});

        execute(createReq(remove, "server-group", sg1, "from", "server-groups", sgs0));
        checkRemove("server-group", sg1, "server-groups", sgs0);
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
            "server-groups", sgs0));
        tlNames.add(lbName);
        checkCreate("tcp-lb", lbName);

        String socks5Name = randomName("s0");
        execute(createReq(add, "socks5-server", socks5Name,
            "acceptor-elg", elg0, "event-loop-group", elg1,
            "address", "127.0.0.1:" + socks5Port,
            "server-groups", sgs0));
        socks5Names.add(socks5Name);
        checkCreate("socks5-server", socks5Name);

        String sg0 = "myexample.com:8080";
        execute(createReq(add, "server-group", sg0,
            "timeout", "500", "period", "200", "up", "2", "down", "5",
            "event-loop-group", elg0));
        sgNames.add(sg0);
        checkCreate("server-group", sg0);

        execute(createReq(add, "server-group", sg0, "to", "server-groups", sgs0, "weight", "10"));
        checkCreate("server-group", sg0, "server-groups", sgs0);

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

    @Test
    public void channelAndStateAndStatistics() throws Exception {
        int port = 7007;
        String lbName = randomName("lb0");
        execute(createReq(add, "tcp-lb", lbName,
            "acceptor-elg", elg0, "event-loop-group", elg1,
            "address", "127.0.0.1:" + port,
            "server-groups", sgs0));
        tlNames.add(lbName);
        checkCreate("tcp-lb", lbName);

        String sg0 = randomName("sg0");
        execute(createReq(add, "server-group", sg0,
            "timeout", "500", "period", "200", "up", "2", "down", "5",
            "event-loop-group", elg0));
        sgNames.add(sg0);
        checkCreate("server-group", sg0);

        execute(createReq(add, "server-group", sg0, "to", "server-groups", sgs0, "weight", "10"));
        checkCreate("server-group", sg0, "server-groups", sgs0);

        execute(createReq(add, "server", "sg7771", "to", "server-group", sg0, "address", "127.0.0.1:7771", "weight", "10"));
        checkCreate("server", "sg7771", "server-group", sg0);
        execute(createReq(add, "server", "sg7772", "to", "server-group", sg0, "address", "127.0.0.1:7772", "weight", "5"));
        checkCreate("server", "sg7772", "server-group", sg0);

        Thread.sleep(500);

        // bind-server
        assertEquals(1, count(createReq(list, "bind-server", "in", "el", "el00", "in", "elg", elg0)));
        assertEquals(1, count(createReq(list, "bind-server", "in", "el", "el01", "in", "elg", elg0)));
        assertEquals(Collections.singletonList("127.0.0.1:" + port), queryList(createReq(list_detail, "bind-server", "in", "el", "el00", "in", "elg", elg0)));
        assertEquals(Collections.singletonList("127.0.0.1:" + port), queryList(createReq(list_detail, "bind-server", "in", "el", "el01", "in", "elg", elg0)));

        assertEquals(2, count(createReq(list, "bind-server", "in", "tcp-lb", lbName)));
        assertEquals(Arrays.asList(
            "127.0.0.1:" + port,
            "127.0.0.1:" + port
        ), queryList(createReq(list_detail, "bind-server", "in", "tcp-lb", lbName)));

        // test socks5 here
        {
            int socks5Port = 7002;
            String socks5Name = randomName("s0");
            execute(createReq(add, "socks5-server", socks5Name,
                "acceptor-elg", elg0, "event-loop-group", elg1,
                "address", "127.0.0.1:" + socks5Port,
                "server-groups", sgs0));
            socks5Names.add(socks5Name);
            checkCreate("socks5-server", socks5Name);

            assertEquals(2, count(createReq(list, "bind-server", "in", "socks5-server", socks5Name)));
            assertEquals(Arrays.asList(
                "127.0.0.1:" + socks5Port,
                "127.0.0.1:" + socks5Port
            ), queryList(createReq(list_detail, "bind-server", "in", "socks5-server", socks5Name)));

            execute(createReq(remove, "socks5-server", socks5Name));
            checkRemove("socks5-server", socks5Name);
            socks5Names.remove(socks5Name);
        }

        // accepted-conn-count = 0
        {
            assertEquals(0,
                count(createReq(list, "accepted-conn-count",
                    "in", "bind-server", "127.0.0.1:" + port, "in", "el", "el00", "in", "elg", elg0)) +
                    count(createReq(list, "accepted-conn-count",
                        "in", "bind-server", "127.0.0.1:" + port, "in", "el", "el01", "in", "elg", elg0))
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
            assertTrue(connections.contains("127.0.0.1:" + sock1.localAddress().port() + "/127.0.0.1:" + port));
            assertTrue(connections.contains("127.0.0.1:" + sock2.localAddress().port() + "/127.0.0.1:" + port));
            assertTrue(connections.contains("127.0.0.1:" + sock3.localAddress().port() + "/127.0.0.1:" + port));

            // lb
            int countSessions = count(createReq(list, "session", "in", "tcp-lb", lbName));
            countConnections = count(createReq(list, "connection", "in", "tcp-lb", lbName));
            assertEquals(3, countSessions);
            assertEquals(6, countConnections);
            connections = queryList(createReq(list_detail, "connection", "in", "tcp-lb", lbName));
            assertEquals(6, connections.size());
            assertTrue(connections.contains("127.0.0.1:" + sock1.localAddress().port() + "/127.0.0.1:" + port));
            assertTrue(connections.contains("127.0.0.1:" + sock2.localAddress().port() + "/127.0.0.1:" + port));
            assertTrue(connections.contains("127.0.0.1:" + sock3.localAddress().port() + "/127.0.0.1:" + port));
            assertEquals(3, querySessions(createReq(list_detail, "session", "in", "tcp-lb", lbName)).size());
            assertTrue(querySessions(createReq(list_detail, "session", "in", "tcp-lb", lbName))
                .stream().flatMap(Collection::stream).collect(Collectors.toSet()).containsAll(connections));

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
                    "in", "bind-server", "127.0.0.1:" + port, "in", "el", "el00", "in", "elg", elg0)) +
                    count(createReq(list, "accepted-conn-count",
                        "in", "bind-server", "127.0.0.1:" + port, "in", "el", "el01", "in", "elg", elg0))
            );
        }

        // bytes-in and bytes-out are not easy to be tested
        // we just leave here a TODO
    }

    @Test
    public void lbWithProtocol() throws Exception {
        int lbPort = 7007;

        String lbName = randomName("lb0");
        execute(createReq(add, "tcp-lb", lbName,
            "acceptor-elg", elg0, "event-loop-group", elg1,
            "address", "127.0.0.1:" + lbPort,
            "server-groups", sgs0,
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

        execute(createReq(add, "server-group", sg0, "to", "server-groups", sgs0, "weight", "10"));
        checkCreate("server-group", sg0, "server-groups", sgs0);

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
}
