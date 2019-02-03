package net.cassite.vproxy.test.cases;

import net.cassite.vproxy.component.check.HealthCheckConfig;
import net.cassite.vproxy.component.exception.NoException;
import net.cassite.vproxy.discovery.Discovery;
import net.cassite.vproxy.discovery.DiscoveryConfig;
import net.cassite.vproxy.discovery.TimeoutConfig;
import net.cassite.vproxy.util.BlockCallback;
import net.cassite.vproxy.util.IPType;
import org.junit.Test;

import static org.junit.Assert.*;

public class TestDiscovery {
    @Test
    public void discover() throws Exception {
        Discovery d0 = new Discovery("d0",
            new DiscoveryConfig(
                "lo0", IPType.v4, 17080, 18080, 18080,
                32, 18080, 18081,
                new TimeoutConfig(20, 1050, 20, 1050, 2000),
                new HealthCheckConfig(1000, 500, 2, 3)
            ));
        Discovery d1 = new Discovery("d1",
            new DiscoveryConfig(
                "lo0", IPType.v4, 17081, 18081, 18081,
                32, 18080, 18081,
                new TimeoutConfig(30, 30, 30, 30, 2000),
                new HealthCheckConfig(1000, 500, 2, 3)
            ));
        // now d0 and d1 not discovered each other
        assertEquals(1, d0.getNodes().size());
        assertEquals("d0", d0.getNodes().get(0).nodeName);
        assertEquals(1, d1.getNodes().size());
        assertEquals("d1", d1.getNodes().get(0).nodeName);
        // sleep for 500ms, then they found each other, but not healthy yet
        Thread.sleep(500);
        assertEquals(2, d0.getNodes().size());
        assertEquals(2, d1.getNodes().size());
        boolean d0Exists = d0.getNodes().stream().anyMatch(n -> n.nodeName.equals("d0") && n.healthy);
        boolean d1Exists = d0.getNodes().stream().anyMatch(n -> n.nodeName.equals("d1") && !n.healthy);
        assertTrue("all nodes found and unhealthy", d0Exists && d1Exists);
        d0Exists = d1.getNodes().stream().anyMatch(n -> n.nodeName.equals("d0") && !n.healthy);
        d1Exists = d1.getNodes().stream().anyMatch(n -> n.nodeName.equals("d1") && n.healthy);
        assertTrue("all nodes found and unhealthy", d0Exists && d1Exists);
        // sleep for 500ms (totally 1s), they should be healthy
        Thread.sleep(550);
        assertEquals(2, d0.getNodes().size());
        assertEquals(2, d1.getNodes().size());
        d0Exists = d0.getNodes().stream().anyMatch(n -> n.nodeName.equals("d0") && n.healthy);
        d1Exists = d0.getNodes().stream().anyMatch(n -> n.nodeName.equals("d1") && n.healthy);
        assertTrue("all nodes found and healthy", d0Exists && d1Exists);
        d0Exists = d1.getNodes().stream().anyMatch(n -> n.nodeName.equals("d0") && n.healthy);
        d1Exists = d1.getNodes().stream().anyMatch(n -> n.nodeName.equals("d1") && n.healthy);
        assertTrue("all nodes found and healthy", d0Exists && d1Exists);

        // stop d0
        BlockCallback<Void, NoException> cb = new BlockCallback<>();
        d0.close(cb);
        cb.block();

        // the d1 should receive leave event
        Thread.sleep(50);
        assertEquals(1, d1.getNodes().size());
        assertEquals("d1", d1.getNodes().get(0).nodeName);

        // stop d1
        // end
        cb = new BlockCallback<>();
        d1.close(cb);
        cb.block();
    }

    @Test
    public void discovery3() throws Exception {
        Discovery d0 = new Discovery("d0",
            new DiscoveryConfig(
                "lo0", IPType.v4, 17080, 18080, 18080,
                32, 18080, 18081,
                new TimeoutConfig(20, 1050, 20, 1050, 2000),
                new HealthCheckConfig(1000, 500, 2, 3)
            ));
        Discovery d1 = new Discovery("d1",
            new DiscoveryConfig(
                "lo0", IPType.v4, 17081, 18081, 18081,
                32, 18080, 18081,
                new TimeoutConfig(30, 30, 30, 30, 2000),
                new HealthCheckConfig(1000, 500, 2, 3)
            ));
        // let them find each other
        Thread.sleep(1100);
        assertEquals(2, d0.getNodes().stream().filter(n -> n.healthy).count());
        assertEquals(2, d1.getNodes().stream().filter(n -> n.healthy).count());

        // create d2 and let it join
        Discovery d2 = new Discovery("d2",
            new DiscoveryConfig(
                "lo0", IPType.v4, 17082, 18082, 18082,
                32, 18080, 18082,
                new TimeoutConfig(/*only send one packet*/1, /*make it very long that won't happen*/Integer.MAX_VALUE, /*only send one packet*/1, /*make it very long that won't happen*/Integer.MAX_VALUE, 2000),
                new HealthCheckConfig(1000, /*1200 > d0/d1 interval*/600, 2, 3)
            ));
        // currently know nothing
        assertEquals(1, d2.getNodes().size());
        Thread.sleep(500); // shorter than d0/d1 interval
        // now d2 should have joined
        assertEquals(3, d2.getNodes().size());
        assertEquals("d0 is first visited by d2, so it knows who d2 is", 3, d0.getNodes().size());
        assertEquals("d1 is not alerted, so it doesn't knows who d2 is", 2, d1.getNodes().size());
        // wait for another 500 ms (1000total) to let d0 notify d1
        Thread.sleep(550);
        assertEquals("now d1 is notified by d0", 3, d1.getNodes().size());
        // wait for another 200 ms (1200 total) to let them all become healthy
        assertEquals(3, d2.getNodes().size());
        assertEquals(3, d2.getNodes().stream().filter(n -> n.healthy).count());

        // close d0
        BlockCallback<Void, NoException> cb = new BlockCallback<>();
        d0.close(cb);
        cb.block();
        Thread.sleep(50);

        // d1 and d2 should remove d0
        assertEquals(0, d1.getNodes().stream().filter(n -> n.nodeName.equals("d0")).count());
        assertEquals(0, d2.getNodes().stream().filter(n -> n.nodeName.equals("d0")).count());

        // cleanup
        cb = new BlockCallback<>();
        d1.close(cb);
        cb.block();
        cb = new BlockCallback<>();
        d2.close(cb);
        cb.block();
    }

    @Test
    public void discoverIPV6() throws Exception {
        Discovery d0 = new Discovery("d0",
            new DiscoveryConfig(
                "lo0", IPType.v6, 17080, 18080, 18080,
                128, 18080, 18081,
                new TimeoutConfig(20, 1050, 20, 1050, 2000),
                new HealthCheckConfig(1000, 500, 2, 3)
            ));
        Discovery d1 = new Discovery("d1",
            new DiscoveryConfig(
                "lo0", IPType.v6, 17081, 18081, 18081,
                128, 18080, 18081,
                new TimeoutConfig(30, 30, 30, 30, 2000),
                new HealthCheckConfig(1000, 500, 2, 3)
            ));
        // now d0 and d1 not discovered each other
        assertEquals(1, d0.getNodes().size());
        assertEquals("d0", d0.getNodes().get(0).nodeName);
        assertEquals(1, d1.getNodes().size());
        assertEquals("d1", d1.getNodes().get(0).nodeName);
        // sleep for 500ms, then they found each other
        Thread.sleep(500);
        assertEquals(2, d0.getNodes().size());
        assertEquals(2, d1.getNodes().size());

        // cleanup
        BlockCallback<Void, NoException> cb = new BlockCallback<>();
        d1.close(cb);
        cb.block();
        cb = new BlockCallback<>();
        d0.close(cb);
        cb.block();
    }
}
