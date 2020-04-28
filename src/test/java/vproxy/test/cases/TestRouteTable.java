package vproxy.test.cases;

import org.junit.Test;
import vproxy.util.Network;
import vproxy.util.Utils;
import vswitch.RouteTable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class TestRouteTable {
    private Network getNetwork(String s) {
        String[] ab = s.split("/");
        String a = ab[0];
        int b = Integer.parseInt(ab[1]);
        return new Network(Utils.parseIpString(a), Utils.parseMask(b));
    }

    private List<String> expect = Arrays.asList(
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
