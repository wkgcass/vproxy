package io.vproxy.test.cases;

import io.vproxy.base.util.Network;
import io.vproxy.base.util.Networks;
import io.vproxy.vfd.IP;
import io.vproxy.vswitch.RouteTable;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestRouteTable {
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
