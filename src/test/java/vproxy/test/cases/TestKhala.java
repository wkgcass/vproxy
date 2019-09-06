package vproxy.test.cases;

import vclient.HttpClient;
import vclient.HttpResponse;
import vjson.JSON;
import vjson.util.ArrayBuilder;
import vjson.util.ObjectBuilder;
import vproxy.component.check.HealthCheckConfig;
import vproxy.component.khala.Khala;
import vproxy.component.khala.KhalaConfig;
import vproxy.component.khala.KhalaNode;
import vproxy.discovery.Discovery;
import vproxy.discovery.DiscoveryConfig;
import vproxy.discovery.Node;
import vproxy.discovery.TimeConfig;
import vproxy.test.tool.DiscoveryHolder;
import vproxy.util.BlockCallback;
import vproxy.util.IPType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import vserver.HttpServer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class TestKhala {
    private DiscoveryHolder holder;

    @Before
    public void setUp() {
        holder = new DiscoveryHolder();
    }

    @After
    public void tearDown() {
        holder.release();
    }

    @Test
    public void addNode() throws Exception {
        Discovery d0 = new Discovery("d0", new DiscoveryConfig(
            "lo0", IPType.v4,
            17080, 18080, 18080,
            32, 18080, 18081,
            new TimeConfig(
                2, 1050,
                2, 1050,
                3000),
            new HealthCheckConfig(200, 1000, 2, 3)
        ));
        holder.add(d0);
        Khala k0 = new Khala(d0, new KhalaConfig(Integer.MAX_VALUE /*disable the periodic check for test*/));
        Discovery d1 = new Discovery("d1", new DiscoveryConfig(
            "lo0", IPType.v4,
            17081, 18081, 18081,
            32, 18080, 18081,
            new TimeConfig(
                2, 1050,
                2, 1050,
                3000),
            new HealthCheckConfig(200, 1000, 2, 3)
        ));
        holder.add(d1);
        Khala k1 = new Khala(d1, new KhalaConfig(Integer.MAX_VALUE /*disable the periodic check for test*/));

        Map<Node, Set<KhalaNode>> nodes0 = k0.getNodeToKhalaNodesMap();
        Map<Node, Set<KhalaNode>> nodes1 = k1.getNodeToKhalaNodesMap();
        assertEquals("only the discovery node it self when just launched", 1, nodes0.size());
        assertEquals(0, nodes0.get(d0.localNode).size());
        assertEquals("only the discovery node it self when just launched", 1, nodes1.size());
        assertEquals(0, nodes1.get(d1.localNode).size());

        // wait until d0 and d1 find each other
        Thread.sleep(2500);

        nodes0 = k0.getNodeToKhalaNodesMap();
        nodes1 = k1.getNodeToKhalaNodesMap();
        assertEquals("khala find each other when discovery node is up", 2, nodes0.size());
        assertEquals(0, nodes0.get(d0.localNode).size());
        assertEquals(0, nodes0.get(d1.localNode).size());
        assertEquals("khala find each other when discovery node is up", 2, nodes1.size());
        assertEquals(0, nodes1.get(d1.localNode).size());
        assertEquals(0, nodes1.get(d0.localNode).size());

        // add khala node
        k0.addLocal(new KhalaNode("s0", "z0", "127.0.0.0", 9990));
        Thread.sleep(1000 /*wait long enough*/);
        nodes0 = k0.getNodeToKhalaNodesMap();
        nodes1 = k1.getNodeToKhalaNodesMap();
        assertEquals(2, nodes0.size());
        assertEquals("khala node added", 1, nodes0.get(d0.localNode).size());
        assertEquals(0, nodes0.get(d1.localNode).size());
        assertEquals(2, nodes1.size());
        assertEquals(0, nodes1.get(d1.localNode).size());
        assertEquals("khala nodes will be learned by other nodes", 1, nodes1.get(d0.localNode).size());

        // add khala node
        k1.addLocal(new KhalaNode("s0", "z0", "127.0.0.1", 9991));
        Thread.sleep(1000 /*wait long enough*/);
        nodes0 = k0.getNodeToKhalaNodesMap();
        nodes1 = k1.getNodeToKhalaNodesMap();
        assertEquals(2, nodes0.size());
        assertEquals(1, nodes0.get(d0.localNode).size());
        assertEquals("k0 learns the node in k1", 1, nodes0.get(d1.localNode).size());
        assertEquals(2, nodes1.size());
        assertEquals(1, nodes1.get(d1.localNode).size());
        assertEquals(1, nodes1.get(d0.localNode).size());
    }

    @Test
    public void addUpdateRemove() throws Exception {
        Discovery d0 = new Discovery("d0", new DiscoveryConfig(
            "lo0", IPType.v4,
            17080, 18080, 18080,
            32, 18080, 18082,
            new TimeConfig(
                200, Integer.MAX_VALUE,
                200, Integer.MAX_VALUE,
                3000),
            new HealthCheckConfig(200, 1000, 2, 3)
        ));
        holder.add(d0);
        Khala k0 = new Khala(d0, new KhalaConfig(Integer.MAX_VALUE /*disable the periodic sync for test*/));
        Discovery d1 = new Discovery("d1", new DiscoveryConfig(
            "lo0", IPType.v4,
            17081, 18081, 18081,
            32, 18080, 18082,
            new TimeConfig(
                200, Integer.MAX_VALUE,
                200, Integer.MAX_VALUE,
                3000),
            new HealthCheckConfig(200, 1000, 2, 3)
        ));
        holder.add(d1);
        Khala k1 = new Khala(d1, new KhalaConfig(Integer.MAX_VALUE /*disable the periodic sync for test*/));
        Discovery d2 = new Discovery("d2", new DiscoveryConfig(
            "lo0", IPType.v4,
            17082, 18082, 18082,
            32, 18080, 18082,
            new TimeConfig(
                1001/*without a sleep*/, Integer.MAX_VALUE,
                200, Integer.MAX_VALUE,
                3000),
            new HealthCheckConfig(200, 1000, 2, 3)
        ));
        holder.add(d2);
        Khala k2 = new Khala(d2, new KhalaConfig(Integer.MAX_VALUE /*disable the periodic sync for test*/));

        Map<Node, Set<KhalaNode>> nodes0 = k0.getNodeToKhalaNodesMap();
        Map<Node, Set<KhalaNode>> nodes1 = k1.getNodeToKhalaNodesMap();
        Map<Node, Set<KhalaNode>> nodes2 = k2.getNodeToKhalaNodesMap();
        assertEquals("only the discovery node it self when just launched", 1, nodes0.size());
        assertEquals(0, nodes0.get(d0.localNode).size());
        assertEquals("only the discovery node it self when just launched", 1, nodes1.size());
        assertEquals(0, nodes1.get(d1.localNode).size());
        assertEquals("only the discovery node it self when just launched", 1, nodes2.size());
        assertEquals(0, nodes2.get(d2.localNode).size());

        // wait until d0 and d1 and d2 find each other
        Thread.sleep(2500);

        nodes0 = k0.getNodeToKhalaNodesMap();
        nodes1 = k1.getNodeToKhalaNodesMap();
        nodes2 = k2.getNodeToKhalaNodesMap();
        assertEquals("khala find each other when discovery node is up", 3, nodes0.size());
        assertEquals(0, nodes0.get(d0.localNode).size());
        assertEquals(0, nodes0.get(d1.localNode).size());
        assertEquals(0, nodes0.get(d2.localNode).size());
        assertEquals("khala find each other when discovery node is up", 3, nodes1.size());
        assertEquals(0, nodes1.get(d0.localNode).size());
        assertEquals(0, nodes1.get(d1.localNode).size());
        assertEquals(0, nodes1.get(d2.localNode).size());
        assertEquals("khala find each other when discovery node is up", 3, nodes2.size());
        assertEquals(0, nodes2.get(d0.localNode).size());
        assertEquals(0, nodes2.get(d1.localNode).size());
        assertEquals(0, nodes2.get(d2.localNode).size());

        // add three khala nodes
        KhalaNode kn1 = new KhalaNode("s0", "z0", "127.0.0.1", 9990);
        k0.addLocal(kn1);
        KhalaNode kn2 = new KhalaNode("s0", "z0", "127.0.0.2", 9991);
        k1.addLocal(kn2);
        KhalaNode kn3 = new KhalaNode("s0", "z0", "127.0.0.3", 9992);
        k2.addLocal(kn3);

        // wait until the data sync
        Thread.sleep(1000 /*wait long enough*/);

        nodes0 = k0.getNodeToKhalaNodesMap();
        nodes1 = k1.getNodeToKhalaNodesMap();
        nodes2 = k2.getNodeToKhalaNodesMap();
        assertEquals(3, nodes0.size());
        assertEquals("nodes sync-ed", 1, nodes0.get(d0.localNode).size());
        assertEquals("nodes sync-ed", 1, nodes0.get(d1.localNode).size());
        assertEquals("nodes sync-ed", 1, nodes0.get(d2.localNode).size());
        assertEquals(3, nodes1.size());
        assertEquals("nodes sync-ed", 1, nodes1.get(d0.localNode).size());
        assertEquals("nodes sync-ed", 1, nodes1.get(d1.localNode).size());
        assertEquals("nodes sync-ed", 1, nodes1.get(d2.localNode).size());
        assertEquals(3, nodes2.size());
        assertEquals("nodes sync-ed", 1, nodes2.get(d0.localNode).size());
        assertEquals("nodes sync-ed", 1, nodes2.get(d1.localNode).size());
        assertEquals("nodes sync-ed", 1, nodes2.get(d2.localNode).size());

        // let's remove a khala node
        k0.removeLocal(kn1);
        Thread.sleep(1000 /*wait long enough*/);
        assertEquals(3, nodes0.size());
        assertEquals(0, nodes0.get(d0.localNode).size());
        assertEquals(1, nodes0.get(d1.localNode).size());
        assertEquals(1, nodes0.get(d2.localNode).size());
        assertEquals(3, nodes1.size());
        assertEquals(0, nodes1.get(d0.localNode).size());
        assertEquals(1, nodes1.get(d1.localNode).size());
        assertEquals(1, nodes1.get(d2.localNode).size());
        assertEquals(3, nodes2.size());
        assertEquals(0, nodes2.get(d0.localNode).size());
        assertEquals(1, nodes2.get(d1.localNode).size());
        assertEquals(1, nodes2.get(d2.localNode).size());

        // let's add a new khala node
        KhalaNode kn1a = new KhalaNode("s0", "z0", "127.0.0.1", 9890);
        k0.addLocal(kn1a);
        Thread.sleep(1000 /*wait long enough*/);

        assertEquals(3, nodes0.size());
        assertEquals(1, nodes0.get(d0.localNode).size());
        assertEquals(1, nodes0.get(d1.localNode).size());
        assertEquals(1, nodes0.get(d2.localNode).size());
        assertEquals(3, nodes1.size());
        assertEquals(1, nodes1.get(d0.localNode).size());
        assertEquals(1, nodes1.get(d1.localNode).size());
        assertEquals(1, nodes1.get(d2.localNode).size());
        assertEquals(3, nodes2.size());
        assertEquals(1, nodes2.get(d0.localNode).size());
        assertEquals(1, nodes2.get(d1.localNode).size());
        assertEquals(1, nodes2.get(d2.localNode).size());

        assertEquals("the node data should update", 9890, nodes2.get(d0.localNode).iterator().next().port);
    }

    @Test
    public void joinNodesWhenUpRemoveNodesWhenDown() throws Exception {
        Discovery d0 = new Discovery("d0", new DiscoveryConfig(
            "lo0", IPType.v4,
            17080, 18080, 18080,
            32, 18080, 18082,
            new TimeConfig(
                200, Integer.MAX_VALUE,
                200, Integer.MAX_VALUE,
                3000),
            new HealthCheckConfig(200, 1000, 2, 3)
        ));
        holder.add(d0);
        Khala k0 = new Khala(d0, new KhalaConfig(Integer.MAX_VALUE /*disable the periodic sync for test*/));
        Discovery d1 = new Discovery("d1", new DiscoveryConfig(
            "lo0", IPType.v4,
            17081, 18081, 18081,
            32, 18080, 18082,
            new TimeConfig(
                200, Integer.MAX_VALUE,
                200, Integer.MAX_VALUE,
                3000),
            new HealthCheckConfig(200, 1000, 2, 3)
        ));
        holder.add(d1);
        Khala k1 = new Khala(d1, new KhalaConfig(Integer.MAX_VALUE /*disable the periodic sync for test*/));
        k0.addLocal(new KhalaNode("s0", "z0", "127.0.0.0", 9990));
        k1.addLocal(new KhalaNode("s0", "z0", "127.0.0.1", 9991));

        // wait for d0 d1 find each other and sync-ed khala
        Thread.sleep(2500);
        Map<Node, Set<KhalaNode>> nodes0 = k0.getNodeToKhalaNodesMap();
        Map<Node, Set<KhalaNode>> nodes1 = k1.getNodeToKhalaNodesMap();
        assertEquals(2, nodes0.size());
        assertEquals(1, nodes0.get(d0.localNode).size());
        assertEquals("will send khala-local when discovery node is UP", 1, nodes0.get(d1.localNode).size());
        assertEquals(2, nodes1.size());
        assertEquals("will send khala-local when discovery node is UP", 1, nodes1.get(d0.localNode).size());
        assertEquals(1, nodes1.get(d1.localNode).size());

        // then we create k1 and directly add noes into khala before it finds d0 d1
        Discovery d2 = new Discovery("d2", new DiscoveryConfig(
            "lo0", IPType.v4,
            17082, 18082, 18082,
            32, 18080, 18082,
            new TimeConfig(
                1001 /*send without a sleep*/, Integer.MAX_VALUE,
                200, Integer.MAX_VALUE,
                3000),
            new HealthCheckConfig(200, 1000, 2, 3)
        ));
        holder.add(d2);
        Khala k2 = new Khala(d2, new KhalaConfig(Integer.MAX_VALUE /*disable the periodic sync for test*/));
        k2.addLocal(new KhalaNode("s0", "z0", "127.0.0.2", 9992));

        Thread.sleep(2500); // wait for sync
        nodes0 = k0.getNodeToKhalaNodesMap();
        nodes1 = k1.getNodeToKhalaNodesMap();
        Map<Node, Set<KhalaNode>> nodes2 = k2.getNodeToKhalaNodesMap();
        assertEquals(3, nodes0.size());
        assertEquals(1, nodes0.get(d0.localNode).size());
        assertEquals(1, nodes0.get(d1.localNode).size());
        assertEquals(1, nodes0.get(d2.localNode).size());
        assertEquals(3, nodes1.size());
        assertEquals(1, nodes1.get(d0.localNode).size());
        assertEquals(1, nodes1.get(d1.localNode).size());
        assertEquals(1, nodes1.get(d2.localNode).size());
        assertEquals(3, nodes2.size());
        assertEquals(1, nodes2.get(d0.localNode).size());
        assertEquals(1, nodes2.get(d1.localNode).size());
        assertEquals(1, nodes2.get(d2.localNode).size());

        // close the d1 socket but do not close it, use reflect
        Field httpServerF = Discovery.class.getDeclaredField("httpServer");
        httpServerF.setAccessible(true);
        HttpServer httpServer = (HttpServer) httpServerF.get(d1);
        httpServer.close();

        // wait for 3000ms, the node should be DOWN
        Thread.sleep(4000);
        nodes0 = k0.getNodeToKhalaNodesMap();
        nodes1 = k1.getNodeToKhalaNodesMap();
        nodes2 = k2.getNodeToKhalaNodesMap();

        assertEquals(2, nodes0.size());
        assertEquals(1, nodes0.get(d0.localNode).size());
        assertFalse(nodes0.containsKey(d1.localNode));
        assertEquals(1, nodes0.get(d2.localNode).size());
        assertEquals("unchanged", 3, nodes1.size());
        assertEquals("unchanged", 1, nodes1.get(d0.localNode).size());
        assertEquals("unchanged", 1, nodes1.get(d1.localNode).size());
        assertEquals("unchanged", 1, nodes1.get(d2.localNode).size());
        assertEquals(2, nodes2.size());
        assertEquals(1, nodes2.get(d0.localNode).size());
        assertFalse(nodes2.containsKey(d1.localNode));
        assertEquals(1, nodes2.get(d2.localNode).size());
    }

    @Test
    public void sync() throws Exception {
        Discovery d0 = new Discovery("d0", new DiscoveryConfig(
            "lo0", IPType.v4,
            17080, 18080, 18080,
            32, 18080, 18082,
            new TimeConfig(
                200, Integer.MAX_VALUE,
                200, Integer.MAX_VALUE,
                3000),
            new HealthCheckConfig(200, 1000, 2, 3)
        ));
        holder.add(d0);
        Khala k0 = new Khala(d0, new KhalaConfig(Integer.MAX_VALUE /*disable the periodic sync for test*/));
        Discovery d1 = new Discovery("d1", new DiscoveryConfig(
            "lo0", IPType.v4,
            17081, 18081, 18081,
            32, 18080, 18082,
            new TimeConfig(
                200, Integer.MAX_VALUE,
                200, Integer.MAX_VALUE,
                3000),
            new HealthCheckConfig(200, 1000, 2, 3)
        ));
        holder.add(d1);
        Khala k1 = new Khala(d1, new KhalaConfig(Integer.MAX_VALUE /*disable the periodic sync for test*/));
        k0.addLocal(new KhalaNode("s0", "z0", "127.0.0.0", 9990));
        k1.addLocal(new KhalaNode("s0", "z0", "127.0.0.1", 9991));

        Thread.sleep(2500); // wait for sync

        // start http client to get hash and snyc
        HttpClient client1 = HttpClient.to("127.0.0.1", 18080);
        HttpClient client2 = HttpClient.to("127.0.0.1", 18081);

        String hash1;
        {
            var cb = new BlockCallback<HttpResponse, IOException>();
            client1.put("/discovery/api/v1/exchange/khala.hash").send(new ObjectBuilder().build(), (err, resp) -> {
                if (err != null) cb.failed(err);
                else cb.succeeded(resp);
            });
            var resp = cb.block();
            assertEquals(200, resp.status());
            hash1 = ((JSON.Object) resp.bodyAsJson()).getString("hash");
        }

        String hash2;
        {
            var cb = new BlockCallback<HttpResponse, IOException>();
            client2.put("/discovery/api/v1/exchange/khala.hash").send(new ObjectBuilder().build(), (err, resp) -> {
                if (err != null) cb.failed(err);
                else cb.succeeded(resp);
            });
            var resp = cb.block();
            assertEquals(200, resp.status());
            hash2 = ((JSON.Object) resp.bodyAsJson()).getString("hash");
        }

        assertEquals(hash1, hash2);

        JSON.Object nodes1Json;
        HashMap<String, Object> nodes1;
        {
            var cb = new BlockCallback<HttpResponse, IOException>();
            client1.put("/discovery/api/v1/exchange/khala.sync").send(new ArrayBuilder().build(), (err, resp) -> {
                if (err != null) cb.failed(err);
                else cb.succeeded(resp);
            });
            var resp = cb.block();
            assertEquals(200, resp.status());
            nodes1Json = (JSON.Object) resp.bodyAsJson();
            nodes1 = nodes1Json.toJavaObject();

            assertTrue(nodes1Json.containsKey("diff"));
        }

        HashMap<String, Object> nodes2;
        {
            var cb = new BlockCallback<HttpResponse, IOException>();
            client2.put("/discovery/api/v1/exchange/khala.sync").send(new ArrayBuilder().build(), (err, resp) -> {
                if (err != null) cb.failed(err);
                else cb.succeeded(resp);
            });
            var resp = cb.block();
            assertEquals(200, resp.status());
            nodes2 = ((JSON.Object) resp.bodyAsJson()).toJavaObject();
        }

        assertEquals(nodes1, nodes2);

        // use reflect to close the http server inside d1
        // close the d1 socket but do not close it, use reflect
        Field httpServerF = Discovery.class.getDeclaredField("httpServer");
        httpServerF.setAccessible(true);
        HttpServer httpServer = (HttpServer) httpServerF.get(d1);
        httpServer.close();

        // and start another one at once
        HttpServer server = HttpServer.create();
        String[] respHash = {hash1}; // initially set to hash1, in this case, the hash req will end without a further sync request
        int[] hashCnt = {0};
        int[] syncCnt = {0};
        server.put("/discovery/api/v1/exchange/khala.hash", rctx -> {
            ++hashCnt[0];
            rctx.response().end(new ObjectBuilder().put("hash", respHash[0]).build());
        });
        server.put("/discovery/api/v1/exchange/khala.sync", rctx -> {
            ++syncCnt[0];
            rctx.response().end(new ObjectBuilder().putArray("diff", a -> {
            }).build());
        });
        server.listen(18081);

        Thread.sleep(1000);

        k0.sync();
        Thread.sleep(1000);
        assertEquals(1, hashCnt[0]);
        assertEquals(0, syncCnt[0]);

        hashCnt[0] = 0;
        syncCnt[0] = 0;

        respHash[0] = "abc";
        k0.sync();
        Thread.sleep(1000);
        assertEquals(1, hashCnt[0]);
        assertEquals(1, syncCnt[0]);
    }
}
