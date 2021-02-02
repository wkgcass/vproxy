package vproxy.test.cases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import vclient.*;
import vfd.IP;
import vfd.IPPort;
import vlibbase.Conn;
import vlibbase.ConnRefPool;
import vproxy.component.app.Socks5Server;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.svrgroup.Upstream;
import vproxybase.component.check.CheckProtocol;
import vproxybase.component.check.HealthCheckConfig;
import vproxybase.component.elgroup.EventLoopGroup;
import vproxybase.component.svrgroup.Method;
import vproxybase.component.svrgroup.ServerGroup;
import vproxybase.util.Annotations;
import vproxybase.util.BlockCallback;
import vproxybase.util.ByteArray;
import vserver.HttpServer;
import vserver.NetServer;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.*;

public class TestConnTransfer {
    private static final int tcpPort = 30080;
    private static final int socks5Port = 31080;
    private static final int httpServer1Port = 33080;

    private ConnRefPool pool;
    private NetClient client;
    private NetServer server;

    private Socks5Server socks5Server;
    private HttpServer httpServer1;
    private SocksClient socksClient;
    private HttpClient httpClient1ToSocks5;
    private HttpClient httpClient2ToSocks5;

    @Before
    public void setUp() throws Exception {
        pool = ConnRefPool.create(10);
        client = NetClient.to("127.0.0.1", tcpPort);
        server = NetServer.create();
        EventLoopGroup elg = new EventLoopGroup("elg");
        elg.add("el");
        Upstream ups = new Upstream("ups");
        ServerGroup sg1 = new ServerGroup("sg1", elg, new HealthCheckConfig(500, 500, 1, 2, CheckProtocol.none), Method.wrr);
        sg1.setAnnotations(new Annotations(Map.of("vproxy/hint-host", "http-server-1.foo.bar", "vproxy/hint-port", "80")));
        sg1.add("httpServer1", new IPPort("127.0.0.1", httpServer1Port), 1);
        ServerGroup sg2 = new ServerGroup("sg2", elg, new HealthCheckConfig(500, 500, 1, 2, CheckProtocol.none), Method.wrr);
        sg2.setAnnotations(new Annotations(Map.of("vproxy/hint-host", "http-server-2.foo.bar", "vproxy/hint-port", "443")));
        sg2.add("httpServer2", new IPPort(IP.blockResolve("www.baidu.com"), 443), 1);
        ups.add(sg1, 1);
        ups.add(sg2, 1);
        socks5Server = new Socks5Server("socks5", elg, elg,
            new IPPort("127.0.0.1", socks5Port), ups,
            2_000, 1024, 1024, SecurityGroup.allowAll());
        socks5Server.start();

        httpServer1 = HttpServer.create();
        httpServer1.get("/", rctx -> rctx.response().end("hello"));
        httpServer1.listen(httpServer1Port);

        socksClient = SocksClient.to("127.0.0.1", socks5Port);
        httpClient1ToSocks5 = HttpClient.to("127.0.0.1", socks5Port);
        httpClient2ToSocks5 = HttpClient.to(new IPPort("127.0.0.1", socks5Port), new HttpClient.Options().setHost("www.baidu.com").setSSL(true));

        Thread.sleep(500);
    }

    @After
    public void tearDown() {
        if (pool != null) {
            pool.close();
        }
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.close();
        }
        if (socks5Server != null) {
            socks5Server.destroy();
        }
        if (httpServer1 != null) {
            httpServer1.close();
        }
        if (socksClient != null) {
            socksClient.close();
        }
        if (httpClient1ToSocks5 != null) {
            httpClient1ToSocks5.close();
        }
        if (httpClient2ToSocks5 != null) {
            httpClient2ToSocks5.close();
        }
    }

    @Test
    public void tcpcli2pool() throws Exception {
        server.accept(conn -> {
            conn.data(conn::write);
            conn.closed(() -> {
            });
        }).listen(tcpPort);

        BlockCallback<Conn, IOException> connCb = new BlockCallback<>();
        client.connect((err, conn) -> {
            if (err != null) {
                connCb.failed(err);
                return;
            }
            connCb.succeeded(conn);
        });
        var conn = connCb.block();

        assertEquals(0, pool.count());
        assertTrue(conn.isValidRef());
        assertFalse(conn.isTransferring());
        conn.transferTo(pool);
        assertFalse(conn.isValidRef());
        assertFalse(conn.isTransferring());
        Thread.sleep(10);
        assertEquals(1, pool.count());

        var refOpt = pool.get();
        assertTrue(refOpt.isPresent());
        assertEquals(0, pool.count());
        var ref = refOpt.get();
        assertTrue(ref.isValidRef());
        assertTrue(ref.isTransferring());
        conn = ref.transferTo(client);
        assertFalse(ref.isValidRef());
        assertFalse(ref.isTransferring());

        BlockCallback<String, IOException> cb = new BlockCallback<>();
        conn.data(data -> cb.succeeded(new String(data.toJavaArray())));
        conn.closed(() -> {
        });
        conn.write(ByteArray.from("abc"));
        assertEquals("abc", cb.block());
        conn.close();
    }

    @Test
    public void socks5DomainAndHttp() throws Exception {
        BlockCallback<String, IOException> block = new BlockCallback<>();
        socksClient.proxy("http-server-1.foo.bar", 80, (err, ref) -> {
            if (err != null) {
                block.failed(err);
                return;
            }
            HttpClientConn conn;
            try {
                conn = ref.transferTo(httpClient1ToSocks5);
            } catch (IOException e) {
                block.failed(e);
                return;
            }
            conn.get("/").send((e, resp) -> {
                if (e != null) {
                    block.failed(e);
                    return;
                }
                block.succeeded(resp.bodyAsString());
            });
        });
        String body = block.block();
        assertEquals("hello", body);
    }

    @Test
    public void socks5IPPortAndHttp() throws Exception {
        BlockCallback<String, IOException> block = new BlockCallback<>();
        socksClient.proxy(new IPPort("127.0.0.1", httpServer1Port), (err, ref) -> {
            if (err != null) {
                block.failed(err);
                return;
            }
            HttpClientConn conn;
            try {
                conn = ref.transferTo(httpClient1ToSocks5);
            } catch (IOException e) {
                block.failed(e);
                return;
            }
            conn.get("/").send((e, resp) -> {
                if (e != null) {
                    block.failed(e);
                    return;
                }
                block.succeeded(resp.bodyAsString());
            });
        });
        String body = block.block();
        assertEquals("hello", body);
    }

    @Test
    public void socks5DomainAndHttps() throws Exception {
        BlockCallback<HttpResponse, IOException> block = new BlockCallback<>();
        socksClient.proxy("http-server-2.foo.bar", 443, (err, ref) -> {
            if (err != null) {
                block.failed(err);
                return;
            }
            HttpClientConn conn;
            try {
                conn = ref.transferTo(httpClient2ToSocks5);
            } catch (IOException e) {
                block.failed(e);
                return;
            }
            conn.get("/").header("user-agent", "curl/vproxy").send((e, resp) -> {
                if (e != null) {
                    block.failed(e);
                    return;
                }
                block.succeeded(resp);
            });
        });
        HttpResponse resp = block.block();
        assertEquals(200, resp.status());
    }
}
