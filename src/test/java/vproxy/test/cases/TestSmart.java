package vproxy.test.cases;

import vproxy.component.auto.AutoConfig;
import vproxy.component.auto.SmartGroupDelegate;
import vproxy.component.auto.SmartServiceDelegate;
import vproxy.component.check.HealthCheckConfig;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.khala.Khala;
import vproxy.component.khala.KhalaConfig;
import vproxy.component.svrgroup.Method;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.discovery.Discovery;
import vproxy.discovery.DiscoveryConfig;
import vproxy.discovery.TimeConfig;
import vproxy.test.tool.DiscoveryHolder;
import vproxy.util.IPType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.NetworkInterface;
import java.util.Enumeration;

import static org.junit.Assert.*;

public class TestSmart {
    private DiscoveryHolder holder;
    private EventLoopGroup elg0;
    private EventLoopGroup elg1;
    private EventLoopGroup elg2;
    private ServerGroup serverGroup1;

    @Before
    public void setUp() throws Exception {
        holder = new DiscoveryHolder();
        elg0 = new EventLoopGroup("elg0");
        elg1 = new EventLoopGroup("elg1");
        elg2 = new EventLoopGroup("elg2");
        serverGroup1 = new ServerGroup("sg0", elg0, new HealthCheckConfig(3000, 1000, 2, 3), Method.wrr);
    }

    @After
    public void tearDown() {
        holder.release();
        elg0.close();
        elg1.close();
        elg2.close();
    }

    private Khala getKhala(String discoveryName, int port) throws Exception {
        Discovery d = new Discovery(discoveryName,
            new DiscoveryConfig(
                "lo0", IPType.v4, port - 1000, port, port,
                32, 18080, 18082,
                new TimeConfig(
                    20, 1000,
                    20, 1000,
                    3000),
                new HealthCheckConfig(200, 200, 2, 3)
            ));
        holder.add(d);
        return new Khala(d, new KhalaConfig(1000));
    }

    public static String loopbackNic() throws Exception {
        Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
        while (nics.hasMoreElements()) {
            NetworkInterface nic = nics.nextElement();
            if (nic.isLoopback()) {
                return nic.getName();
            }
        }
        throw new Exception("no loopback nic");
    }

    @Test
    public void learnBackends() throws Exception {
        Khala k0 = getKhala("d0", 18080);
        Khala k1 = getKhala("d1", 18081);

        SmartGroupDelegate smartGroupDelegate = new SmartGroupDelegate("alb", "s0", "z0", serverGroup1, new AutoConfig(k0));
        assertEquals("have no svr", 0, serverGroup1.getServerHandles().size());

        // add service
        SmartServiceDelegate smartServiceDelegate = new SmartServiceDelegate("myservice0", "s0", "z0", loopbackNic(), IPType.v4, 19080, new AutoConfig(k1));

        Thread.sleep(3000 /*wait long enough*/);
        // now the smartGroupDelegate should have created an lb named s0, with group named s0, with a svr s0@127.0.0.1:19080
        assertEquals("have one svr now", 1, serverGroup1.getServerHandles().size());
        assertEquals("name pattern `$service@$addr:$port`", "s0@127.0.0.1:19080", serverGroup1.getServerHandles().get(0).alias);

        smartGroupDelegate.destroy();
        smartServiceDelegate.destroy();
    }

    @Test
    public void willNotLearnIfDoNotCare() throws Exception {
        Khala k0 = getKhala("d0", 18080);
        Khala k1 = getKhala("d1", 18081);

        SmartGroupDelegate smartGroupDelegate = new SmartGroupDelegate("alb", "s0", "z0", serverGroup1, new AutoConfig(k0));

        // same zone different service
        SmartServiceDelegate smartServiceDelegate0 = new SmartServiceDelegate("myservice0", "sx", "z0", loopbackNic(), IPType.v4, 12345, new AutoConfig(k1));
        // same service different zone
        SmartServiceDelegate smartServiceDelegate1 = new SmartServiceDelegate("myservice1", "s0", "zx", loopbackNic(), IPType.v4, 12346, new AutoConfig(k1));

        Thread.sleep(3000 /*wait long enough*/);

        assertEquals("have no svr", 0, serverGroup1.getServerHandles().size());

        smartGroupDelegate.destroy();
        smartServiceDelegate0.destroy();
        smartServiceDelegate1.destroy();
    }
}
