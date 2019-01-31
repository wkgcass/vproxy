package net.cassite.vproxy.test.cases;

import net.cassite.vproxy.component.app.Socks5Server;
import net.cassite.vproxy.component.check.HealthCheckConfig;
import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.secure.SecurityGroup;
import net.cassite.vproxy.component.svrgroup.Method;
import net.cassite.vproxy.component.svrgroup.ServerGroup;
import net.cassite.vproxy.component.svrgroup.ServerGroups;
import net.cassite.vproxy.connection.NetEventLoop;
import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.socks.AddressType;
import net.cassite.vproxy.test.tool.IdServer;
import net.cassite.vproxy.test.tool.Socks5Client;
import org.junit.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestSocks5 {
    private static final int lbPort = 18080;

    private static SelectorEventLoop serverLoop;

    private ServerGroups sgs0;
    private EventLoopGroup elg0;
    private ServerGroup sg0;
    private ServerGroup domainDotComGroup;
    private Socks5Server socks5;

    private List<Socks5Client> clients = new LinkedList<>();

    @BeforeClass
    public static void classSetUp() throws Exception {
        serverLoop = SelectorEventLoop.open();
        new Thread(serverLoop::loop, "serverLoop").start();
        NetEventLoop serverNetLoop = new NetEventLoop(serverLoop);
        new IdServer("0", serverNetLoop, 19080);
        new IdServer("1", serverNetLoop, 19081, "::1");
        new IdServer("2", serverNetLoop, 19082);
        new IdServer("3", serverNetLoop, 19083);
    }

    @AfterClass
    public static void classTearDown() throws Exception {
        Thread t = serverLoop.runningThread;
        serverLoop.close();
        t.join();
    }

    @Before
    public void setUp() throws Exception {
        sgs0 = new ServerGroups("sgs0");
        elg0 = new EventLoopGroup("elg0");
        elg0.add("el0");
        sg0 = new ServerGroup("sg0", elg0, new HealthCheckConfig(400, /* disable health check */24 * 60 * 60 * 1000, 2, 3), Method.wrr);
        sg0.add("svr0", new InetSocketAddress("127.0.0.1", 19080), InetAddress.getByName("127.0.0.1"), 10);
        sg0.add("svr1", new InetSocketAddress("::1", 19081), InetAddress.getByName("::1"), 10);
        // manually set to healthy
        for (ServerGroup.ServerHandle h : sg0.getServerHandles()) {
            h.healthy = true;
        }
        domainDotComGroup = new ServerGroup("domain.com:80", elg0, new HealthCheckConfig(400, /* disable health check */24 * 60 * 60 * 1000, 2, 3), Method.wrr);
        domainDotComGroup.add("svr2", new InetSocketAddress("127.0.0.1", 19082), InetAddress.getByName("127.0.0.1"), 10);
        domainDotComGroup.add("svr3", new InetSocketAddress("127.0.0.1", 19083), InetAddress.getByName("127.0.0.1"), 10);
        // manually set to healthy
        for (ServerGroup.ServerHandle h : domainDotComGroup.getServerHandles()) {
            h.healthy = true;
        }

        // connection will not spread between groups
        // so just add them
        sgs0.add(sg0, 10);
        sgs0.add(domainDotComGroup, 10);

        socks5 = new Socks5Server(
            "socks5", elg0, elg0,
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 18080),
            sgs0, 16384, 16384, SecurityGroup.allowAll()
        );
        socks5.start();
    }

    @After
    public void tearDown() {
        elg0.close();
        for (Socks5Client c : clients) {
            c.close();
        }
    }

    @Test
    public void requestIpv4() throws Exception {
        for (int i = 0; i < 100; ++i) {
            Socks5Client client = new Socks5Client(lbPort);
            client.connect(AddressType.ipv4, "127.0.0.1", 19080);
            String res = client.sendAndRecv("anything", 1);
            assertEquals("the result will always be 0", "0", res);
            client.close();
        }
    }

    @Test
    public void requestIpv6() throws Exception {
        for (int i = 0; i < 100; ++i) {
            Socks5Client client = new Socks5Client(lbPort);
            client.connect(AddressType.ipv6, "::1", 19081);
            String res = client.sendAndRecv("anything", 1);
            assertEquals("the result will always be 1", "1", res);
            client.close();
        }
    }

    @Test
    public void proxyDomain() throws Exception {
        int two = 0;
        int three = 0;
        for (int i = 0; i < 100; ++i) {
            Socks5Client client = new Socks5Client(lbPort);
            client.connect(AddressType.domain, "domain.com", 80);
            String res = client.sendAndRecv("anything", 1);
            assertTrue("result is two or three", res.equals("2") || res.equals("3"));
            if (res.equals("3")) {
                ++three;
            } else {
                ++two;
            }
            client.close();
        }
        assertTrue("weight same, so two count and three count should be the same",
            three - two > -2 && two - three < 2);
    }
}
