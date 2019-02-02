package net.cassite.vproxy.test.cases;

import net.cassite.vproxy.discovery.Discovery;
import net.cassite.vproxy.discovery.DiscoveryConfig;
import org.junit.Test;

public class TestDiscovery {
    @Test
    public void discover() throws Exception {
        Discovery d0 = new Discovery("d0",
            new DiscoveryConfig(
                "lo0", "127.0.0.1", 17080, 18080, 18080,
                "127.0.0.1", 32, 18080, 18082
            ));
        Discovery d1 = new Discovery("d1",
            new DiscoveryConfig(
                "lo0", "127.0.0.1", 17081, 18081, 18081,
                "127.0.0.1", 32, 18080, 18082
            ));
        Discovery d2 = new Discovery("d2",
            new DiscoveryConfig(
                "lo0", "127.0.0.1", 17082, 18082, 18082,
                "127.0.0.1", 32, 18080, 18082
            ));
        while (true) {
            Thread.sleep(3000);
            System.out.println("d0 -> " + d0.getNodes());
            System.out.println("d1 -> " + d1.getNodes());
            System.out.println("d2 -> " + d2.getNodes());
        }
    }
}
