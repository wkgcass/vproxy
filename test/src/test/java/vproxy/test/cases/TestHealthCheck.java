package vproxy.test.cases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import vfd.IPPort;
import vproxybase.component.check.CheckProtocol;
import vproxybase.component.check.HealthCheckConfig;
import vproxybase.component.elgroup.EventLoopGroup;
import vproxybase.component.svrgroup.Method;
import vproxybase.component.svrgroup.ServerGroup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestHealthCheck {
    private EventLoopGroup eventLoopGroup;
    private ServerGroup serverGroup;

    @Before
    public void setUp() throws Exception {
        eventLoopGroup = new EventLoopGroup("elg");
        eventLoopGroup.add("el0");
    }

    @After
    public void tearDown() {
        if (serverGroup != null) {
            serverGroup.clear();
        }
        if (eventLoopGroup != null) {
            eventLoopGroup.close();
        }
    }

    @Test
    public void dns() throws Exception {
        serverGroup = new ServerGroup("sg0", eventLoopGroup, new HealthCheckConfig(1000, 1000, 2, 3, CheckProtocol.dns), Method.wrr);
        serverGroup.add("a", new IPPort("8.8.8.8", 53), 10);
        serverGroup.add("b", new IPPort("8.8.4.4", 53), 10);
        serverGroup.add("c", new IPPort("127.0.0.1", 33241), 10);
        assertEquals(3, serverGroup.getServerHandles().size());
        assertEquals(0, serverGroup.getServerHandles().stream().filter(s -> s.healthy).count());
        Thread.sleep(3000);
        assertEquals(3, serverGroup.getServerHandles().size());
        assertEquals(2, serverGroup.getServerHandles().stream().filter(s -> s.healthy).count());

        ServerGroup.ServerHandle h = null;
        for (var svr : serverGroup.getServerHandles()) {
            if (!svr.healthy) {
                h = svr;
                break;
            }
        }
        assertNotNull(h);
        assertEquals(33241, h.server.getPort());
    }
}
