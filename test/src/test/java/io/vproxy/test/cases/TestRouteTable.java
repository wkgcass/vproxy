package io.vproxy.test.cases;

import io.vproxy.base.util.Network;
import io.vproxy.base.util.Networks;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.exception.XException;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vfd.jdk.ChannelFDs;
import io.vproxy.vswitch.RouteTable;
import io.vproxy.vswitch.VirtualNetwork;
import io.vproxy.vswitch.SwitchDelegate;
import io.vproxy.vswitch.node.NodeGraph;
import io.vproxy.vswitch.node.NodeGraphScheduler;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class TestRouteTable {
    private static VirtualNetwork network(SelectorEventLoop loop, boolean ipv6) throws Exception {
        var constructor = SwitchDelegate.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] args = new Object[constructor.getParameterCount()];
        args[1] = new NodeGraphScheduler(new NodeGraph());
        args[6] = (SwitchDelegate.GetSelectorEventLoop) () -> loop;
        var delegate = (SwitchDelegate) constructor.newInstance(args);
        return new VirtualNetwork(delegate, 1, loop.ensureNetEventLoop(),
            Network.from("10.250.0.0/24"), ipv6 ? Network.from("2001:db8:1::/64") : null,
            60_000, 60_000, null);
    }

    @Test
    public void ipv6LinkLocalGatewayCanRouteGlobalTraffic() throws Exception {
        try (var loop = SelectorEventLoop.open(ChannelFDs.get())) {
            var network = network(loop, true);
            var gateway = IP.from("fe80::1");
            var mac = new MacAddress("02:00:00:00:00:01");
            network.routeTable.addRule(new RouteTable.RouteRule("gw6", Network.from("::/0"), gateway));
            network.arpTable.record(mac, gateway, true);
            assertEquals(gateway, network.routeTable.lookup(IP.from("2001:db8:2::2")).ip);
            assertEquals(mac, network.lookup(gateway));
            network.arpTable.clearCache();
        }
    }

    @Test
    public void gatewayExceptionDoesNotAdmitOffLinkGlobalOrWrongFamilyRoutes() throws Exception {
        try (var loop = SelectorEventLoop.open(ChannelFDs.get())) {
            for (boolean ipv6 : new boolean[]{false, true}) {
                var network = network(loop, ipv6);
                for (String gateway : new String[]{"2001:db8:2::1", "10.251.0.1", "169.254.0.1", "ff02::1", "::"}) {
                    try {
                        network.routeTable.addRule(new RouteTable.RouteRule("bad", Network.from("::/0"), IP.from(gateway)));
                        fail("must reject gateway " + gateway);
                    } catch (XException expected) {
                        assertNull(network.routeTable.lookup(IP.from("2001:db8:2::2")));
                    }
                }
                if (!ipv6) {
                    try {
                        network.routeTable.addRule(new RouteTable.RouteRule("bad", Network.from("::/0"), IP.from("fe80::1")));
                        fail("IPv4-only network cannot use an IPv6 link-local gateway");
                    } catch (XException expected) {
                        assertNull(network.routeTable.lookup(IP.from("2001:db8:2::2")));
                    }
                }
            }
        }
    }

    private static class R implements Networks.Rule {
        final int n;

        R(int n) {
            this.n = n;
        }
    }

    @Test
    public void networksV4() {
        var nets = new Networks<R>();
        nets.add(Network.from("192.168.1.0/24"), new R(1));
        nets.add(Network.from("192.168.0.0/16"), new R(2));
        nets.add(Network.from("192.168.1.128/25"), new R(3));

        assertEquals(nets.lookup(IP.from("192.168.1.1")).n, 1);
        assertEquals(nets.lookup(IP.from("192.168.2.1")).n, 2);
        assertEquals(nets.lookup(IP.from("192.168.1.129")).n, 3);

        assertNull(nets.lookup(IP.from("10.0.0.1")));

        assertEquals(nets.lookup(IP.from("::ffff:192.168.1.1")).n, 1);
        assertEquals(nets.lookup(IP.from("::ffff:192.168.2.1")).n, 2);
        assertEquals(nets.lookup(IP.from("::ffff:192.168.1.129")).n, 3);

        assertEquals(nets.lookup(IP.from("::192.168.1.1")).n, 1);
        assertEquals(nets.lookup(IP.from("::192.168.2.1")).n, 2);
        assertEquals(nets.lookup(IP.from("::192.168.1.129")).n, 3);
    }

    @Test
    public void networksV6() {
        var nets = new Networks<R>();
        nets.add(Network.from("2001::1:0/120"), new R(1));
        nets.add(Network.from("2001::1:0/112"), new R(2));
        nets.add(Network.from("2001::1:0080/124"), new R(3));

        assertEquals(nets.lookup(IP.from("2001::1:1")).n, 1);
        assertEquals(nets.lookup(IP.from("2001::1:0101")).n, 2);
        assertEquals(nets.lookup(IP.from("2001::1:0081")).n, 3);

        assertNull(nets.lookup(IP.from("2001::2:1")));
    }

    private Network getNetwork(String s) {
        String[] ab = s.split("/");
        String a = ab[0];
        int b = Integer.parseInt(ab[1]);
        return Network.from(IP.parseIpString(a), Network.parseMask(b));
    }

    private final List<String> expect = Arrays.asList(
        "192.168.3.0/24",
        "192.168.0.0/16",
        "0.0.0.0/0"
    );

    @Test
    public void ordering() throws Exception {
        RouteTable table = new RouteTable();
        table.addRule(new RouteTable.RouteRule("a", getNetwork("192.168.0.0/16"), 1));
        table.addRule(new RouteTable.RouteRule("b", getNetwork("192.168.3.0/24"), 1));
        table.addRule(new RouteTable.RouteRule("c", getNetwork("0.0.0.0/0"), 1));
        var actual = table.getRules().stream().map(x -> x.rule.toString()).collect(Collectors.toList());
        assertEquals(expect, actual);
    }

    @Test
    public void ordering2() throws Exception {
        var table = new RouteTable();
        table.addRule(new RouteTable.RouteRule("a", getNetwork("192.168.0.0/16"), 1));
        table.addRule(new RouteTable.RouteRule("c", getNetwork("0.0.0.0/0"), 1));
        table.addRule(new RouteTable.RouteRule("b", getNetwork("192.168.3.0/24"), 1));
        var actual = table.getRules().stream().map(x -> x.rule.toString()).collect(Collectors.toList());
        assertEquals(expect, actual);
    }
}
