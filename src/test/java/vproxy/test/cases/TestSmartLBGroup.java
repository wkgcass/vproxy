package vproxy.test.cases;

import vproxy.component.app.Socks5Server;
import vproxy.component.app.TcpLB;
import vproxy.component.auto.AutoConfig;
import vproxy.component.auto.Sidecar;
import vproxy.component.auto.SmartLBGroup;
import vproxy.component.check.HealthCheckConfig;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.exception.StillRunningException;
import vproxy.component.khala.Khala;
import vproxy.component.khala.KhalaConfig;
import vproxy.component.khala.KhalaNode;
import vproxy.component.khala.KhalaNodeType;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.svrgroup.Method;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.component.svrgroup.ServerGroups;
import vproxy.discovery.Discovery;
import vproxy.discovery.DiscoveryConfig;
import vproxy.discovery.TimeoutConfig;
import vproxy.test.tool.DiscoveryHolder;
import vproxy.util.IPType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.junit.Assert.*;

public class TestSmartLBGroup {
    private DiscoveryHolder holder;
    private EventLoopGroup elg0;
    private EventLoopGroup elg1;
    private EventLoopGroup elg2;
    private ServerGroup serverGroup1;
    private ServerGroup serverGroup2;
    private ServerGroup serverGroup3;

    @Before
    public void setUp() throws Exception {
        holder = new DiscoveryHolder();
        elg0 = new EventLoopGroup("elg0");
        elg1 = new EventLoopGroup("elg1");
        elg2 = new EventLoopGroup("elg2");
        serverGroup1 = new ServerGroup("sg0", elg0, new HealthCheckConfig(3000, 1000, 2, 3), Method.wrr);
        serverGroup2 = new ServerGroup("sg1", elg0, new HealthCheckConfig(3000, 1000, 2, 3), Method.wrr);
        serverGroup3 = new ServerGroup("sg2", elg0, new HealthCheckConfig(3000, 1000, 2, 3), Method.wrr);
    }

    @After
    public void tearDown() {
        holder.release();
        elg0.close();
        elg1.close();
        elg2.close();
    }

    private TcpLB getLB(int listenPort, ServerGroup group) throws Exception {
        ServerGroups sgs = new ServerGroups("sgs0");
        sgs.add(group, 10);
        return new TcpLB("tl0", elg0, elg0, new InetSocketAddress(InetAddress.getByName("127.0.0.1"), listenPort),
            sgs, 1000, 16384, 16384, SecurityGroup.allowAll());
        // NOTE: lb is not started because we don't have to start an lb in this test
    }

    private Khala getKhala(String discoveryName, int port) throws Exception {
        Discovery d = new Discovery(discoveryName,
            new DiscoveryConfig(
                "lo0", IPType.v4, port - 1000, port, port,
                32, 18080, 18082,
                new TimeoutConfig(
                    20, 1000,
                    20, 1000,
                    3000),
                new HealthCheckConfig(200, 200, 2, 3)
            ));
        holder.add(d);
        return new Khala(d, new KhalaConfig(1000));
    }

    @Test
    public void learnBackends() throws Exception {
        Khala k0 = getKhala("d0", 18080);
        Khala k1 = getKhala("d1", 18081);

        SmartLBGroup smartLBGroup = new SmartLBGroup("alb", "s0", "z0", getLB(16080, serverGroup1), serverGroup1, new AutoConfig(
            elg0, elg0, k0, "lo0", IPType.v4,
            new HealthCheckConfig(200, 500, 2, 3),
            Method.wrr
        ));
        TcpLB lb = smartLBGroup.handledLb;
        assertEquals("have one group", 1, lb.backends.getServerGroups().size());
        assertEquals("weight -eq 10 fixed in code", 10, lb.backends.getServerGroups().get(0).getWeight());
        assertEquals("have no svr", 0, serverGroup1.getServerHandles().size());

        // add a node
        k1.addLocal(new KhalaNode(KhalaNodeType.pylon, "s0", "z0", "127.0.0.1", 19080));

        Thread.sleep(3000 /*wait long enough*/);
        // now the smartLBGroup should have created an lb named s0, with group named s0, with a svr s0@127.0.0.1:19080
        assertEquals("have one svr now", 1, serverGroup1.getServerHandles().size());
        assertEquals("name pattern `$service@$addr:$port`", "s0@127.0.0.1:19080", serverGroup1.getServerHandles().get(0).alias);

        smartLBGroup.destroy();
    }

    @Test
    public void willNotLearnIfDoNotCare() throws Exception {
        Khala k0 = getKhala("d0", 18080);
        Khala k1 = getKhala("d1", 18081);

        SmartLBGroup smartLBGroup = new SmartLBGroup("alb", "s0", "z0", getLB(16080, serverGroup1), serverGroup1, new AutoConfig(
            elg0, elg0, k0, "lo0", IPType.v4,
            new HealthCheckConfig(200, 500, 2, 3),
            Method.wrr
        ));

        // same zone different service
        k1.addLocal(new KhalaNode(KhalaNodeType.pylon, "sx", "z0", "127.0.0.1", 12345));
        // same service different zone
        k1.addLocal(new KhalaNode(KhalaNodeType.pylon, "s0", "zx", "127.0.0.1", 12346));
        // same service same zone but is nexus
        k1.addLocal(new KhalaNode(KhalaNodeType.nexus, "s0", "z0", "127.0.0.1", 12347));

        Thread.sleep(3000 /*wait long enough*/);

        assertEquals("have no svr", 0, serverGroup1.getServerHandles().size());

        smartLBGroup.destroy();
    }

    @Test
    public void learnNexus() throws Exception {
        Khala k0 = getKhala("d0", 18080);
        Khala k1 = getKhala("d1", 18081);

        Sidecar sidecar = new Sidecar("sidecar", "z0", 16080, new AutoConfig(
            elg0, elg0, k0, "lo0", IPType.v4,
            new HealthCheckConfig(200, 500, 2, 3),
            Method.wrr
        ), 19080, 19089);
        Socks5Server socks5Server = sidecar.socks5Server;
        assertEquals("socks5 have no group", 0, socks5Server.backends.getServerGroups().size());
        assertEquals("sidecar have no lbs", 0, sidecar.getTcpLBs().size());

        SmartLBGroup alb10 = new SmartLBGroup("alb10", "s0", "z0", getLB(28080, serverGroup1), serverGroup1, new AutoConfig(
            elg1, elg1, k1, "lo0", IPType.v4,
            new HealthCheckConfig(200, 500, 2, 3),
            Method.wrr
        ));
        TcpLB lb10 = alb10.handledLb;
        assertEquals("have one group", 1, lb10.backends.getServerGroups().size());
        assertEquals("weight -eq 10 fixed in code", 10, lb10.backends.getServerGroups().get(0).getWeight());
        assertEquals("have no svr", 0, lb10.backends.getServerGroups().get(0).group.getServerHandles().size());

        Thread.sleep(3000 /*wait long enough*/);
        // now sidecar and alb10 should find each other

        assertEquals("socks5 have one group", 1, socks5Server.backends.getServerGroups().size());
        assertEquals("socks5 have one group: s0", "s0", socks5Server.backends.getServerGroups().get(0).group.alias);
        assertEquals("s0 have one server", 1, socks5Server.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals("s0 have one server: the lb", 28080,
            socks5Server.backends.getServerGroups().get(0).group.getServerHandles().get(0).server.getPort());

        assertEquals("the smartLBGroup10 still has no backends", 0, lb10.backends.getServerGroups().get(0).group.getServerHandles().size());

        // now create another smart-lb-group with the same service
        SmartLBGroup alb20 = new SmartLBGroup("alb21", "s0", "z0", getLB(28081, serverGroup2), serverGroup2, new AutoConfig(
            elg2, elg2, k1, "lo0", IPType.v4,
            new HealthCheckConfig(200, 500, 2, 3),
            Method.wrr
        ));
        TcpLB lb20 = alb20.handledLb;

        Thread.sleep(1000 /*wait long enough*/);

        assertEquals("socks5 have one group", 1, socks5Server.backends.getServerGroups().size());
        assertEquals("socks5 have one group: s0", "s0", socks5Server.backends.getServerGroups().get(0).group.alias);
        assertEquals("s0 have two servers", 2, socks5Server.backends.getServerGroups().get(0).group.getServerHandles().size());

        assertEquals("the smartLBGroup20 has no backends", 0, lb20.backends.getServerGroups().get(0).group.getServerHandles().size());

        // now create another smart-lb-group with the same service
        SmartLBGroup alb11 = new SmartLBGroup("alb12", "s1", "z0", getLB(28082, serverGroup3), serverGroup3, new AutoConfig(
            elg1, elg1, k1, "lo0", IPType.v4,
            new HealthCheckConfig(200, 500, 2, 3),
            Method.wrr
        ));
        TcpLB lb11 = alb11.handledLb;

        Thread.sleep(1000 /*wait long enough*/);

        assertEquals("socks5 have two groups", 2, socks5Server.backends.getServerGroups().size());
        assertEquals("socks5 have two groups: s0/s1", "s1", socks5Server.backends.getServerGroups().get(1).group.alias);
        assertEquals("s1 have one server", 1, socks5Server.backends.getServerGroups().get(1).group.getServerHandles().size());

        assertEquals("the smartLBGroup11 has no backends", 0, lb11.backends.getServerGroups().get(0).group.getServerHandles().size());

        // add service s0 into sidecar
        sidecar.addService("s0", 22080);

        assertEquals("sidecar have 1 lb", 1, sidecar.getTcpLBs().size());
        TcpLB lbs0 = sidecar.getTcpLBs().get(0);
        assertEquals("port should start at min port", 19080, lbs0.bindAddress.getPort());
        assertEquals(1, lbs0.backends.getServerGroups().size());
        assertEquals(1, lbs0.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals("the lbs0 backend should be the local service",
            22080, lbs0.backends.getServerGroups().get(0).group.getServerHandles().get(0).server.getPort());

        Thread.sleep(1000 /*wait long enough*/);

        // now the lb10 and lb20 should learn the s0 and add it to backend
        assertEquals("the smartLBGroup10 has 1 backend", 1, lb10.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals(19080, lb10.backends.getServerGroups().get(0).group.getServerHandles().get(0).server.getPort());
        assertEquals("the smartLBGroup20 has 1 backend", 1, lb20.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals(19080, lb20.backends.getServerGroups().get(0).group.getServerHandles().get(0).server.getPort());
        // lb11 should still have only 1 backend
        assertEquals("the smartLBGroup11 still has 0 backend", 0, lb11.backends.getServerGroups().get(0).group.getServerHandles().size());

        // add service s1 into sidecar
        sidecar.addService("s1", 22081);
        assertEquals("sidecar have two lbs", 2, sidecar.getTcpLBs().size());
        TcpLB lbs1 = sidecar.getTcpLBs().get(1);
        assertEquals("port should start at min port and +1", 19081, lbs1.bindAddress.getPort());
        assertEquals(1, lbs1.backends.getServerGroups().size());
        assertEquals(1, lbs1.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals("the lbs1 backend should be the local service",
            22081, lbs1.backends.getServerGroups().get(0).group.getServerHandles().get(0).server.getPort());

        Thread.sleep(1000 /*wait long enough*/);

        // now the lb11 should learn the s1 and add it to backend
        assertEquals("the smartLBGroup11 has 1 backend", 1, lb11.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals(19081, lb11.backends.getServerGroups().get(0).group.getServerHandles().get(0).server.getPort());
        // lb10/20 should still have only 1 backend
        assertEquals("the smartLBGroup10 still has 1 backend", 1, lb10.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals("the smartLBGroup20 still has 1 backend", 1, lb20.backends.getServerGroups().get(0).group.getServerHandles().size());

        // add another service s0 into sidecar
        sidecar.addService("s0", 22082);
        assertEquals("sidecar still has two lbs", 2, sidecar.getTcpLBs().size());
        assertEquals(1, lbs0.backends.getServerGroups().size());
        assertEquals(2, lbs0.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals("the lbs0 backend should be the local service",
            22082, lbs0.backends.getServerGroups().get(0).group.getServerHandles().get(1).server.getPort());

        Thread.sleep(1000 /*wait long enough*/);

        // the lbs should not change
        assertEquals("the smartLBGroup11 still has 1 backend", 1, lb11.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals("the smartLBGroup10 still has 1 backend", 1, lb10.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals("the smartLBGroup20 still has 1 backend", 1, lb20.backends.getServerGroups().get(0).group.getServerHandles().size());

        // remove a service that is still running
        try {
            sidecar.removeService("s0");
            fail();
        } catch (StillRunningException ignore) {
            // should fail
        }

        // maintain 22080 (s0)
        sidecar.maintain("s0", 22080);
        assertEquals("sidecar still has two lbs", 2, sidecar.getTcpLBs().size());
        assertEquals(1, lbs0.backends.getServerGroups().size());
        assertEquals("one of the servers is removed", 1, lbs0.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals("the lbs0 backend should be the local service",
            22082, lbs0.backends.getServerGroups().get(0).group.getServerHandles().get(0).server.getPort());

        Thread.sleep(1000 /*wait long enough*/);

        // the lbs should not change
        assertEquals("the smartLBGroup11 still has 1 backend", 1, lb11.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals("the smartLBGroup10 still has 1 backend", 1, lb10.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals("the smartLBGroup20 still has 1 backend", 1, lb20.backends.getServerGroups().get(0).group.getServerHandles().size());

        // maintain 22082 (s0)
        sidecar.maintain("s0", 22082);
        assertEquals("sidecar still have two lbs", 2, sidecar.getTcpLBs().size());
        assertEquals(1, lbs0.backends.getServerGroups().size());
        assertEquals("servers removed, but one in _MAINTAIN_ mode", 1, lbs0.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertNotNull("servers removed, but one in _MAINTAIN_ mode", lbs0.backends.getServerGroups().get(0).group.getServerHandles().get(0).data);

        Thread.sleep(1000 /*wait long enough*/);

        // the s0 pylon node should be removed
        assertEquals("the smartLBGroup11 still has 1 backend", 1, lb11.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals("the smartLBGroup10 has 0 backend", 0, lb10.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals("the smartLBGroup20 has 0 backend", 0, lb20.backends.getServerGroups().get(0).group.getServerHandles().size());

        // add 22080 (s0) back
        sidecar.addService("s0", 22080);
        assertEquals("sidecar still have two lbs", 2, sidecar.getTcpLBs().size());
        assertEquals(1, lbs0.backends.getServerGroups().size());
        assertEquals("the _MAINTAIN_ server should be removed", 1, lbs0.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertNull("the _MAINTAIN_ server should be removed", lbs0.backends.getServerGroups().get(0).group.getServerHandles().get(0).data);
        assertEquals("the _MAINTAIN_ server should be removed", 22080, lbs0.backends.getServerGroups().get(0).group.getServerHandles().get(0).server.getPort());

        Thread.sleep(1000 /*wait long enough*/);

        // the s0 pylon node should be learned again
        assertEquals("the smartLBGroup11 still has 1 backend", 1, lb11.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals("the smartLBGroup10 has 1 backend", 1, lb10.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals("the smartLBGroup20 has 1 backend", 1, lb20.backends.getServerGroups().get(0).group.getServerHandles().size());

        // remove the 22080 (s0) then add it back (s0)
        sidecar.maintain("s0", 22080); // into _MAINTAIN_ mode
        sidecar.addService("s0", 22080); // remove _MAINTAIN_ flag
        assertEquals("sidecar still have two lbs", 2, sidecar.getTcpLBs().size());
        assertEquals(1, lbs0.backends.getServerGroups().size());
        assertEquals("no _MAINTAIN_ servers", 1, lbs0.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertNull("the _MAINTAIN_ servers", lbs0.backends.getServerGroups().get(0).group.getServerHandles().get(0).data);
        assertEquals("the _MAINTAIN_ servers", 22080, lbs0.backends.getServerGroups().get(0).group.getServerHandles().get(0).server.getPort());

        Thread.sleep(1000 /*wait long enough*/);

        // nothing changes on lb side
        assertEquals("the smartLBGroup11 still has 1 backend", 1, lb11.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals("the smartLBGroup10 still has 1 backend", 1, lb10.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals("the smartLBGroup20 still has 1 backend", 1, lb20.backends.getServerGroups().get(0).group.getServerHandles().size());

        // remove the 22081 (s1)
        sidecar.maintain("s1", 22081); // into _MAINTAIN_ mode
        assertEquals("sidecar still have two lbs", 2, sidecar.getTcpLBs().size());
        sidecar.removeService("s1");
        assertEquals(1, sidecar.getTcpLBs().size());
        assertEquals("the group should be cleared", 0, lbs1.backends.getServerGroups().get(0).group.getServerHandles().size());

        Thread.sleep(1000 /*wait long enough*/);

        // the s1 should be removed from the lb
        assertEquals("the smartLBGroup11 has 0 backend", 0, lb11.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals("the smartLBGroup10 still has 1 backend", 1, lb10.backends.getServerGroups().get(0).group.getServerHandles().size());
        assertEquals("the smartLBGroup20 still has 1 backend", 1, lb20.backends.getServerGroups().get(0).group.getServerHandles().size());

        sidecar.destory();
        alb10.destroy();
        alb20.destroy();
        alb11.destroy();
    }

    @Test
    public void willNotLearnNexusIfDoNotCare() throws Exception {
        Khala k0 = getKhala("d0", 18080);
        Khala k1 = getKhala("d1", 18081);

        Sidecar sidecar = new Sidecar("sidecar", "z0", 16080, new AutoConfig(
            elg0, elg0, k0, "lo0", IPType.v4,
            new HealthCheckConfig(200, 500, 2, 3),
            Method.wrr
        ), 19080, 19089);
        Sidecar sidecar2 = new Sidecar("sidecar2", "z1", 16081, new AutoConfig(
            elg0, elg0, k0, "lo0", IPType.v4,
            new HealthCheckConfig(200, 500, 2, 3),
            Method.wrr
        ), 19090, 19099);
        SmartLBGroup alb = new SmartLBGroup("alb", "s0", "z2", getLB(28080, serverGroup1), serverGroup1, new AutoConfig(
            elg1, elg1, k1, "lo0", IPType.v4,
            new HealthCheckConfig(200, 500, 2, 3),
            Method.wrr
        ));

        Thread.sleep(3000 /*wait long enough*/);

        Socks5Server socks5Server = sidecar.socks5Server;
        assertEquals("socks5 have no group", 0, socks5Server.backends.getServerGroups().size());
        assertEquals("sidecar have no lbs", 0, sidecar.getTcpLBs().size());

        Socks5Server socks5Server2 = sidecar2.socks5Server;
        assertEquals("socks5 have no group", 0, socks5Server2.backends.getServerGroups().size());
        assertEquals("sidecar have no lbs", 0, sidecar2.getTcpLBs().size());

        TcpLB lb10 = alb.handledLb;
        assertEquals("have no svr", 0, lb10.backends.getServerGroups().get(0).group.getServerHandles().size());

        sidecar.destory();
        sidecar2.destory();
        alb.destroy();
    }
}
