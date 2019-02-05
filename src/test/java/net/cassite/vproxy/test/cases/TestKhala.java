package net.cassite.vproxy.test.cases;

import net.cassite.vproxy.component.check.HealthCheckConfig;
import net.cassite.vproxy.component.khala.Khala;
import net.cassite.vproxy.component.khala.KhalaConfig;
import net.cassite.vproxy.component.khala.KhalaNode;
import net.cassite.vproxy.component.khala.KhalaNodeType;
import net.cassite.vproxy.connection.BindServer;
import net.cassite.vproxy.discovery.Discovery;
import net.cassite.vproxy.discovery.DiscoveryConfig;
import net.cassite.vproxy.discovery.Node;
import net.cassite.vproxy.discovery.TimeoutConfig;
import net.cassite.vproxy.test.tool.DiscoveryHolder;
import net.cassite.vproxy.util.IPType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

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
            new TimeoutConfig(
                2, 1050,
                2, 1050,
                3000),
            new HealthCheckConfig(200, 500, 2, 3)
        ));
        holder.add(d0);
        Khala k0 = new Khala(d0, new KhalaConfig(Integer.MAX_VALUE /*disable the periodic check for test*/));
        Discovery d1 = new Discovery("d1", new DiscoveryConfig(
            "lo0", IPType.v4,
            17081, 18081, 18081,
            32, 18080, 18081,
            new TimeoutConfig(
                2, 1050,
                2, 1050,
                3000),
            new HealthCheckConfig(200, 500, 2, 3)
        ));
        holder.add(d1);
        Khala k1 = new Khala(d1, new KhalaConfig(Integer.MAX_VALUE /*disable the periodic check for test*/));

        Map<Node, Set<KhalaNode>> nodes0 = k0.getKhalaNodes();
        Map<Node, Set<KhalaNode>> nodes1 = k1.getKhalaNodes();
        assertEquals("only the discovery node it self when just launched", 1, nodes0.size());
        assertEquals(0, nodes0.get(d0.localNode).size());
        assertEquals("only the discovery node it self when just launched", 1, nodes1.size());
        assertEquals(0, nodes1.get(d1.localNode).size());

        // wait until d0 and d1 find each other
        Thread.sleep(1250);

        nodes0 = k0.getKhalaNodes();
        nodes1 = k1.getKhalaNodes();
        assertEquals("khala find each other when discovery node is up", 2, nodes0.size());
        assertEquals(0, nodes0.get(d0.localNode).size());
        assertEquals(0, nodes0.get(d1.localNode).size());
        assertEquals("khala find each other when discovery node is up", 2, nodes1.size());
        assertEquals(0, nodes1.get(d1.localNode).size());
        assertEquals(0, nodes1.get(d0.localNode).size());

        // add pylon node
        k0.addLocal(new KhalaNode(KhalaNodeType.pylon, "s0", "z0", "127.0.0.0", 9990));
        Thread.sleep(1000 /*wait long enough*/);
        nodes0 = k0.getKhalaNodes();
        nodes1 = k1.getKhalaNodes();
        assertEquals(2, nodes0.size());
        assertEquals("pylon node added", 1, nodes0.get(d0.localNode).size());
        assertEquals(0, nodes0.get(d1.localNode).size());
        assertEquals(2, nodes1.size());
        assertEquals("pylon nodes will not spread", 0, nodes1.get(d1.localNode).size());
        assertEquals(0, nodes1.get(d0.localNode).size());

        // add nexus node
        k1.addLocal(new KhalaNode(KhalaNodeType.nexus, "s0", "z0", "127.0.0.1", 9991));
        Thread.sleep(1000 /*wait long enough*/);
        nodes0 = k0.getKhalaNodes();
        nodes1 = k1.getKhalaNodes();
        assertEquals(2, nodes0.size());
        assertEquals(1, nodes0.get(d0.localNode).size());
        assertEquals("k0 learns the nexus node", 1, nodes0.get(d1.localNode).size());
        assertEquals(2, nodes1.size());
        assertEquals(1, nodes1.get(d1.localNode).size());
        assertEquals("nexus node added and learns the pylon node", 1, nodes1.get(d0.localNode).size());
    }

    @Test
    public void addUpdateRemove() throws Exception {
        Discovery d0 = new Discovery("d0", new DiscoveryConfig(
            "lo0", IPType.v4,
            17080, 18080, 18080,
            32, 18080, 18082,
            new TimeoutConfig(
                200, Integer.MAX_VALUE,
                200, Integer.MAX_VALUE,
                3000),
            new HealthCheckConfig(200, 500, 2, 3)
        ));
        holder.add(d0);
        Khala k0 = new Khala(d0, new KhalaConfig(Integer.MAX_VALUE /*disable the periodic sync for test*/));
        Discovery d1 = new Discovery("d1", new DiscoveryConfig(
            "lo0", IPType.v4,
            17081, 18081, 18081,
            32, 18080, 18082,
            new TimeoutConfig(
                200, Integer.MAX_VALUE,
                200, Integer.MAX_VALUE,
                3000),
            new HealthCheckConfig(200, 500, 2, 3)
        ));
        holder.add(d1);
        Khala k1 = new Khala(d1, new KhalaConfig(Integer.MAX_VALUE /*disable the periodic sync for test*/));
        Discovery d2 = new Discovery("d2", new DiscoveryConfig(
            "lo0", IPType.v4,
            17082, 18082, 18082,
            32, 18080, 18082,
            new TimeoutConfig(
                1001/*without a sleep*/, Integer.MAX_VALUE,
                200, Integer.MAX_VALUE,
                3000),
            new HealthCheckConfig(200, 500, 2, 3)
        ));
        holder.add(d2);
        Khala k2 = new Khala(d2, new KhalaConfig(Integer.MAX_VALUE /*disable the periodic sync for test*/));

        Map<Node, Set<KhalaNode>> nodes0 = k0.getKhalaNodes();
        Map<Node, Set<KhalaNode>> nodes1 = k1.getKhalaNodes();
        Map<Node, Set<KhalaNode>> nodes2 = k2.getKhalaNodes();
        assertEquals("only the discovery node it self when just launched", 1, nodes0.size());
        assertEquals(0, nodes0.get(d0.localNode).size());
        assertEquals("only the discovery node it self when just launched", 1, nodes1.size());
        assertEquals(0, nodes1.get(d1.localNode).size());
        assertEquals("only the discovery node it self when just launched", 1, nodes2.size());
        assertEquals(0, nodes2.get(d2.localNode).size());

        // wait until d0 and d1 and d2 find each other
        Thread.sleep(1050);

        nodes0 = k0.getKhalaNodes();
        nodes1 = k1.getKhalaNodes();
        nodes2 = k2.getKhalaNodes();
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

        // add one nexus node and two pylon nodes
        KhalaNode pylon0 = new KhalaNode(KhalaNodeType.pylon, "s0", "z0", "127.0.0.0", 9990);
        k0.addLocal(pylon0);
        KhalaNode nexus1 = new KhalaNode(KhalaNodeType.nexus, "s0", "z0", "127.0.0.1", 9991);
        k1.addLocal(nexus1);
        KhalaNode pylon2 = new KhalaNode(KhalaNodeType.pylon, "s0", "z0", "127.0.0.2", 9992);
        k2.addLocal(pylon2);

        // wait until the data sync
        Thread.sleep(1000 /*wait long enough*/);

        nodes0 = k0.getKhalaNodes();
        nodes1 = k1.getKhalaNodes();
        nodes2 = k2.getKhalaNodes();
        assertEquals(3, nodes0.size());
        assertEquals("nodes sync-ed", 1, nodes0.get(d0.localNode).size());
        assertEquals("nodes sync-ed", 1, nodes0.get(d1.localNode).size());
        assertEquals("pylon nodes don't spread", 0, nodes0.get(d2.localNode).size());
        assertEquals(3, nodes1.size());
        assertEquals("nodes sync-ed", 1, nodes1.get(d0.localNode).size());
        assertEquals("nodes sync-ed", 1, nodes1.get(d1.localNode).size());
        assertEquals("pylon nodes don't spread", 1, nodes1.get(d2.localNode).size());
        assertEquals(3, nodes2.size());
        assertEquals("pylon nodes don't spread", 0, nodes2.get(d0.localNode).size());
        assertEquals("nodes sync-ed", 1, nodes2.get(d1.localNode).size());
        assertEquals("nodes sync-ed", 1, nodes2.get(d2.localNode).size());

        k2.sync(); // when sync, it will send khala-local to k0, so k0 knows k2 nodes
        Thread.sleep(1000 /*wait long enough*/);
        assertEquals(3, nodes0.size());
        assertEquals(1, nodes0.get(d0.localNode).size());
        assertEquals(1, nodes0.get(d1.localNode).size());
        assertEquals("nodes snyc-ed", 1, nodes0.get(d2.localNode).size());
        assertEquals(3, nodes2.size());
        assertEquals("nodes snyc-ed", 1, nodes2.get(d0.localNode).size());
        assertEquals(1, nodes2.get(d1.localNode).size());
        assertEquals(1, nodes2.get(d2.localNode).size());

        // let's remove a pylon node
        k0.removeLocal(pylon0);
        Thread.sleep(1000 /*wait long enough*/);
        assertEquals(3, nodes0.size());
        assertEquals(0, nodes0.get(d0.localNode).size());
        assertEquals(1, nodes0.get(d1.localNode).size());
        assertEquals(1, nodes0.get(d2.localNode).size());
        assertEquals(3, nodes1.size());
        assertEquals("nexus nodes always notified", 0, nodes1.get(d0.localNode).size());
        assertEquals(1, nodes1.get(d1.localNode).size());
        assertEquals(1, nodes1.get(d2.localNode).size());
        assertEquals(3, nodes2.size());
        assertEquals("pylon nodes don't spread", 1, nodes2.get(d0.localNode).size());
        assertEquals(1, nodes2.get(d1.localNode).size());
        assertEquals(1, nodes2.get(d2.localNode).size());

        // let's add a new pylon
        KhalaNode pylon01 = new KhalaNode(KhalaNodeType.pylon, "s0", "z0", "127.0.0.0", 9890);
        k0.addLocal(pylon01);
        k2.sync();
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
        assertEquals("node sync-ed", 1, nodes2.get(d0.localNode).size());
        assertEquals(1, nodes2.get(d1.localNode).size());
        assertEquals(1, nodes2.get(d2.localNode).size());

        assertEquals("the node data should update", 9890, nodes2.get(d0.localNode).iterator().next().port);

        // let's remove the nexus
        k1.removeLocal(nexus1);
        Thread.sleep(1000 /*wait long enough*/);

        assertEquals(3, nodes0.size());
        assertEquals(1, nodes0.get(d0.localNode).size());
        assertEquals("sync-ed", 0, nodes0.get(d1.localNode).size());
        assertEquals(1, nodes0.get(d2.localNode).size());
        assertEquals(3, nodes1.size());
        assertEquals(1, nodes1.get(d0.localNode).size());
        assertEquals(0, nodes1.get(d1.localNode).size());
        assertEquals(1, nodes1.get(d2.localNode).size());
        assertEquals(3, nodes2.size());
        assertEquals(1, nodes2.get(d0.localNode).size());
        assertEquals("sync-ed", 0, nodes2.get(d1.localNode).size());
        assertEquals(1, nodes2.get(d2.localNode).size());

        // remove pylon now
        k0.removeLocal(pylon01);
        k2.sync(); // will have no effect
        Thread.sleep(1000 /*wait long enough*/);

        assertEquals(3, nodes0.size());
        assertEquals(0, nodes0.get(d0.localNode).size());
        assertEquals(0, nodes0.get(d1.localNode).size());
        assertEquals(1, nodes0.get(d2.localNode).size());
        assertEquals(3, nodes1.size());
        assertEquals(1, nodes1.get(d0.localNode).size());
        assertEquals(0, nodes1.get(d1.localNode).size());
        assertEquals(1, nodes1.get(d2.localNode).size());
        assertEquals(3, nodes2.size());
        assertEquals("will not sync", 1, nodes2.get(d0.localNode).size());
        assertEquals(0, nodes2.get(d1.localNode).size());
        assertEquals(1, nodes2.get(d2.localNode).size());
    }

    @Test
    public void joinNodesWhenUpRemoveNodesWhenDown() throws Exception {
        Discovery d0 = new Discovery("d0", new DiscoveryConfig(
            "lo0", IPType.v4,
            17080, 18080, 18080,
            32, 18080, 18082,
            new TimeoutConfig(
                200, Integer.MAX_VALUE,
                200, Integer.MAX_VALUE,
                3000),
            new HealthCheckConfig(200, 500, 2, 3)
        ));
        holder.add(d0);
        Khala k0 = new Khala(d0, new KhalaConfig(Integer.MAX_VALUE /*disable the periodic sync for test*/));
        Discovery d1 = new Discovery("d1", new DiscoveryConfig(
            "lo0", IPType.v4,
            17081, 18081, 18081,
            32, 18080, 18082,
            new TimeoutConfig(
                200, Integer.MAX_VALUE,
                200, Integer.MAX_VALUE,
                3000),
            new HealthCheckConfig(200, 500, 2, 3)
        ));
        holder.add(d1);
        Khala k1 = new Khala(d1, new KhalaConfig(Integer.MAX_VALUE /*disable the periodic sync for test*/));
        // add pylons to prevent a report to nexus
        k0.addLocal(new KhalaNode(KhalaNodeType.pylon, "s0", "z0", "127.0.0.0", 9990));
        k1.addLocal(new KhalaNode(KhalaNodeType.pylon, "s0", "z0", "127.0.0.1", 9991));

        // wait for d0 d1 find each other and sync-ed khala
        Thread.sleep(1050);
        Map<Node, Set<KhalaNode>> nodes0 = k0.getKhalaNodes();
        Map<Node, Set<KhalaNode>> nodes1 = k1.getKhalaNodes();
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
            new TimeoutConfig(
                1001 /*send without a sleep*/, Integer.MAX_VALUE,
                200, Integer.MAX_VALUE,
                3000),
            new HealthCheckConfig(200, 500, 2, 3)
        ));
        holder.add(d2);
        Khala k2 = new Khala(d2, new KhalaConfig(Integer.MAX_VALUE /*disable the periodic sync for test*/));
        k2.addLocal(new KhalaNode(KhalaNodeType.pylon, "s0", "z0", "127.0.0.2", 9992));

        Thread.sleep(1050); // wait for sync
        nodes0 = k0.getKhalaNodes();
        nodes1 = k1.getKhalaNodes();
        Map<Node, Set<KhalaNode>> nodes2 = k2.getKhalaNodes();
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
        Field tcpServerF = Discovery.class.getDeclaredField("tcpServer");
        tcpServerF.setAccessible(true);
        BindServer tcpServer = (BindServer) tcpServerF.get(d1);
        tcpServer.close();

        // wait for 1500ms, the node should be DOWN
        Thread.sleep(1550);
        nodes0 = k0.getKhalaNodes();
        nodes1 = k1.getKhalaNodes();
        nodes2 = k2.getKhalaNodes();

        assertEquals(2, nodes0.size());
        assertEquals(1, nodes0.get(d0.localNode).size());
        // assertEquals(1, nodes0.get(d1.localNode).size());
        assertEquals(1, nodes0.get(d2.localNode).size());
        assertEquals("unchanged", 3, nodes1.size());
        assertEquals("unchanged", 1, nodes1.get(d0.localNode).size());
        assertEquals("unchanged", 1, nodes1.get(d1.localNode).size());
        assertEquals("unchanged", 1, nodes1.get(d2.localNode).size());
        assertEquals(2, nodes2.size());
        assertEquals(1, nodes2.get(d0.localNode).size());
        // assertEquals(1, nodes2.get(d1.localNode).size());
        assertEquals(1, nodes2.get(d2.localNode).size());
    }
}
