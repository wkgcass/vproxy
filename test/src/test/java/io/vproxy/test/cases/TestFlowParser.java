package io.vproxy.test.cases;

import org.junit.Test;
import io.vproxy.vproxyx.pktfiltergen.flow.FlowParser;
import io.vproxy.vproxyx.pktfiltergen.flow.Flows;

import static org.junit.Assert.assertEquals;

public class TestFlowParser {
    private void testFlow(String input, String expected) throws Exception {
        var parser = new FlowParser(input);
        var flow = parser.parse();
        assertEquals(expected, flow.toString());

        parser = new FlowParser(expected);
        flow = parser.parse();
        assertEquals(expected, flow.toString());
    }

    @Test
    public void table() throws Exception {
        testFlow("table=1,action=normal",
            "table=1,priority=0,actions=normal");
    }

    @Test
    public void priority() throws Exception {
        testFlow("priority=1,action=normal",
            "table=0,priority=1,actions=normal");
    }

    @Test
    public void in_port() throws Exception {
        testFlow("in_port=xdp:veth0,action=normal",
            "table=0,priority=0,in_port=xdp:veth0,actions=normal");
    }

    @Test
    public void dl_dst() throws Exception {
        testFlow("dl_dst=ab:cd:ef:ab:cd:ef,action=normal",
            "table=0,priority=0,dl_dst=ab:cd:ef:ab:cd:ef,actions=normal");
        testFlow("dl_dst=0xabcdefabcdef,action=normal",
            "table=0,priority=0,dl_dst=ab:cd:ef:ab:cd:ef,actions=normal");
        testFlow("dl_dst=aa:bb:cc:ee:00:00/ff:ff:ff:fe:00:00,action=normal",
            "table=0,priority=0,dl_dst=aa:bb:cc:ee:00:00/ff:ff:ff:fe:00:00,actions=normal");
        testFlow("dl_dst=0xaabbccee0000/0xfffffffe0000,action=normal",
            "table=0,priority=0,dl_dst=aa:bb:cc:ee:00:00/ff:ff:ff:fe:00:00,actions=normal");
        testFlow("dl_dst=101010111100110111101111101010111100110111101111,action=normal",
            "table=0,priority=0,dl_dst=ab:cd:ef:ab:cd:ef,actions=normal");
        testFlow("dl_dst=101010101011101111001100111011100000000000000000/111111111111111111111111111111100000000000000000,action=normal",
            "table=0,priority=0,dl_dst=aa:bb:cc:ee:00:00/ff:ff:ff:fe:00:00,actions=normal");
        testFlow("dl_dst=0xaabbccee0000/111111111111111111111111111111100000000000000000,action=normal",
            "table=0,priority=0,dl_dst=aa:bb:cc:ee:00:00/ff:ff:ff:fe:00:00,actions=normal");
        testFlow("dl_dst=101010101011101111001100111011100000000000000000/0xfffffffe0000,action=normal",
            "table=0,priority=0,dl_dst=aa:bb:cc:ee:00:00/ff:ff:ff:fe:00:00,actions=normal");
    }

    @Test
    public void dl_src() throws Exception {
        testFlow("dl_src=ab:cd:ef:ab:cd:ef,action=normal",
            "table=0,priority=0,dl_src=ab:cd:ef:ab:cd:ef,actions=normal");
    }

    @Test
    public void dl_type() throws Exception {
        testFlow("dl_type=0x123,action=normal",
            "table=0,priority=0,dl_type=0x0123,actions=normal");
        testFlow("ip,action=normal",
            "table=0,priority=0,ip,actions=normal");
    }

    @Test
    public void arp_op() throws Exception {
        testFlow("arp_op=1,action=normal",
            "table=0,priority=0,arp,arp_op=0x01,actions=normal");
    }

    @Test
    public void arp_spa() throws Exception {
        testFlow("arp_spa=1.2.3.4,action=normal",
            "table=0,priority=0,arp,arp_spa=1.2.3.4,actions=normal");
        testFlow("arp_spa=1.2.3.0/24,action=normal",
            "table=0,priority=0,arp,arp_spa=1.2.3.0/24,actions=normal");
    }

    @Test
    public void arp_tpa() throws Exception {
        testFlow("arp_tpa=1.2.3.4,action=normal",
            "table=0,priority=0,arp,arp_tpa=1.2.3.4,actions=normal");
    }

    @Test
    public void arp_sha() throws Exception {
        testFlow("arp_sha=ab:cd:ef:ab:cd:ef,action=normal",
            "table=0,priority=0,arp,arp_sha=ab:cd:ef:ab:cd:ef,actions=normal");
        testFlow("arp_sha=ab:cd:ef:00:00:00/ff:ff:ff:00:00:00,action=normal",
            "table=0,priority=0,arp,arp_sha=ab:cd:ef:00:00:00/ff:ff:ff:00:00:00,actions=normal");
    }

    @Test
    public void arp_tha() throws Exception {
        testFlow("arp_tha=ab:cd:ef:ab:cd:ef,action=normal",
            "table=0,priority=0,arp,arp_tha=ab:cd:ef:ab:cd:ef,actions=normal");
    }

    @Test
    public void nw_src() throws Exception {
        testFlow("nw_src=1.2.3.4,action=normal",
            "table=0,priority=0,ip,nw_src=1.2.3.4,actions=normal");
        testFlow("nw_src=fd00:abcd::1,action=normal",
            "table=0,priority=0,ipv6,nw_src=fd00:abcd::1,actions=normal");
        testFlow("nw_src=1.2.3.0/24,action=normal",
            "table=0,priority=0,ip,nw_src=1.2.3.0/24,actions=normal");
        testFlow("nw_src=fd00:abcd::/64,action=normal",
            "table=0,priority=0,ipv6,nw_src=fd00:abcd::/64,actions=normal");
        testFlow("nw_src=0xabcdef01,action=normal",
            "table=0,priority=0,ip,nw_src=171.205.239.1,actions=normal");
        testFlow("nw_src=10101011110011011110111100000001,action=normal",
            "table=0,priority=0,ip,nw_src=171.205.239.1,actions=normal");
        testFlow("nw_src=10101011110011011110111100000000/11111111111111111111111100000000,action=normal",
            "table=0,priority=0,ip,nw_src=171.205.239.0/24,actions=normal");
        testFlow("nw_src=10101011110011011110111100000000/11111111111111111111111101000000,action=normal",
            "table=0,priority=0,ip,nw_src=0xabcdef00/0xffffff40,actions=normal");
        testFlow("ipv6,nw_src=0xfd00abcd000000000000000000000000/0xffffffffffffffff0000000000000000,action=normal",
            "table=0,priority=0,ipv6,nw_src=fd00:abcd::/64,actions=normal");
    }

    @Test
    public void nw_dst() throws Exception {
        testFlow("nw_dst=1.2.3.4,action=normal",
            "table=0,priority=0,ip,nw_dst=1.2.3.4,actions=normal");
        testFlow("nw_dst=fd00:abcd::1,action=normal",
            "table=0,priority=0,ipv6,nw_dst=fd00:abcd::1,actions=normal");
    }

    @Test
    public void nw_proto() throws Exception {
        testFlow("tcp,action=normal",
            "table=0,priority=0,ip,tcp,actions=normal");
        testFlow("tcp6,action=normal",
            "table=0,priority=0,ipv6,tcp6,actions=normal");
        testFlow("udp,action=normal",
            "table=0,priority=0,ip,udp,actions=normal");
        testFlow("udp6,action=normal",
            "table=0,priority=0,ipv6,udp6,actions=normal");
        testFlow("icmp,action=normal",
            "table=0,priority=0,ip,icmp,actions=normal");
        testFlow("icmp6,action=normal",
            "table=0,priority=0,ipv6,icmp6,actions=normal");
    }

    @Test
    public void tp_src() throws Exception {
        testFlow("tcp,tp_src=65535,action=normal",
            "table=0,priority=0,ip,tcp,tp_src=65535,actions=normal");
        testFlow("udp,tp_src=65535,action=normal",
            "table=0,priority=0,ip,udp,tp_src=65535,actions=normal");
        testFlow("tcp6,tp_src=1234,action=normal",
            "table=0,priority=0,ipv6,tcp6,tp_src=1234,actions=normal");
        testFlow("udp6,tp_src=1234,action=normal",
            "table=0,priority=0,ipv6,udp6,tp_src=1234,actions=normal");

        testFlow("tcp,tp_src=0x11f8/0xfff8,action=normal",
            "table=0,priority=0,ip,tcp,tp_src=0x11f8/0xfff8,actions=normal");
    }

    @Test
    public void tp_dst() throws Exception {
        testFlow("tcp,tp_dst=65535,action=normal",
            "table=0,priority=0,ip,tcp,tp_dst=65535,actions=normal");
    }

    @Test
    public void predicate() throws Exception {
        testFlow("predicate=xxx,action=normal",
            "table=0,priority=0,predicate=xxx,actions=normal");
    }

    @Test
    public void vni() throws Exception {
        testFlow("vni=1,action=normal",
            "table=0,priority=0,vni=1,actions=normal");
    }

    @Test
    public void normal() throws Exception {
        testFlow("action=NORMAL",
            "table=0,priority=0,actions=normal");
        testFlow("action=normal",
            "table=0,priority=0,actions=normal");
    }

    @Test
    public void drop() throws Exception {
        testFlow("action=DROP",
            "table=0,priority=0,actions=drop");
        testFlow("action=drop",
            "table=0,priority=0,actions=drop");
    }

    @Test
    public void goto_table() throws Exception {
        testFlow("action=goto_table:1",
            "table=0,priority=0,actions=goto_table:1");
    }

    @Test
    public void mod_dl_dst() throws Exception {
        testFlow("action=mod_dl_dst:ab:cd:ef:ab:cd:ef,normal",
            "table=0,priority=0,actions=mod_dl_dst:ab:cd:ef:ab:cd:ef,normal");
    }

    @Test
    public void mod_dl_src() throws Exception {
        testFlow("action=mod_dl_src:ab:cd:ef:ab:cd:ef,normal",
            "table=0,priority=0,actions=mod_dl_src:ab:cd:ef:ab:cd:ef,normal");
    }

    @Test
    public void mod_nw_src() throws Exception {
        testFlow("ip,action=mod_nw_src:1.2.3.4,normal",
            "table=0,priority=0,ip,actions=mod_nw_src:1.2.3.4,normal");
        testFlow("ipv6,action=mod_nw_src:abcd::,normal",
            "table=0,priority=0,ipv6,actions=mod_nw_src:abcd::,normal");
    }

    @Test
    public void mod_nw_dst() throws Exception {
        testFlow("ip,action=mod_nw_dst:1.2.3.4,normal",
            "table=0,priority=0,ip,actions=mod_nw_dst:1.2.3.4,normal");
        testFlow("ipv6,action=mod_nw_dst:abcd::,normal",
            "table=0,priority=0,ipv6,actions=mod_nw_dst:abcd::,normal");
    }

    @Test
    public void mod_tp_src() throws Exception {
        testFlow("tcp,action=mod_tp_src:1234,normal",
            "table=0,priority=0,ip,tcp,actions=mod_tp_src:1234,normal");
    }

    @Test
    public void mod_tp_dst() throws Exception {
        testFlow("tcp,action=mod_tp_dst:1234,normal",
            "table=0,priority=0,ip,tcp,actions=mod_tp_dst:1234,normal");
    }

    @Test
    public void output() throws Exception {
        testFlow("action=output:xdp:veth0",
            "table=0,priority=0,actions=output:xdp:veth0");
    }

    @Test
    public void multipleActions() throws Exception {
        testFlow("tcp,action=mod_dl_dst:ab:cd:ef:ab:cd:ef,mod_tp_src:112,drop",
            "table=0,priority=0,ip,tcp,actions=mod_dl_dst:ab:cd:ef:ab:cd:ef,mod_tp_src:112,drop");
    }

    private void testFlowBunch(String input, String expected) throws Exception {
        var flows = new Flows();
        flows.add(input);
        assertEquals(expected, flows.toString());

        flows = new Flows();
        flows.add(expected);
        assertEquals(expected, flows.toString());
    }

    @Test
    public void testRateLimitBPS() throws Exception {
        testFlow("action=limit_bps:1048576,pass",
            "table=0,priority=0,actions=limit_bps:1048576,normal");
    }

    @Test
    public void testRateLimitPPS() throws Exception {
        testFlow("action=limit_pps:1000000,pass",
            "table=0,priority=0,actions=limit_pps:1000000,normal");
    }

    @Test
    public void testRun() throws Exception {
        testFlow("action=run:my_method,pass",
            "table=0,priority=0,actions=run:my_method,normal");
    }

    @Test
    public void testInvoke() throws Exception {
        testFlow("action=invoke:my_method",
            "table=0,priority=0,actions=invoke:my_method");
    }

    @Test
    public void tableJump() throws Exception {
        testFlowBunch("" +
                "action=goto_table:1\n" +
                "table=1,action=normal",
            "" +
                "table=0,priority=0,actions=goto_table:1\n" +
                "table=1,priority=0,actions=normal");
    }

    // copied from my current ovs flows at home
    static final String TEST_FLOW = "" +
        "table=0,priority=0,action=goto_table:1\n" +
        "table=0,priority=1000,ipv6,action=drop\n" +
        "table=0,priority=1000,icmp6,action=drop\n" +
        "table=0,priority=1000,arp,action=goto_table:1\n" +
        "table=0,priority=1000,udp,tp_src=68,tp_dst=67,action=goto_table:1\n" +
        "table=0,priority=1000,udp,tp_dst=68,tp_src=67,action=goto_table:1\n" +
        "table=0,priority=1000,icmp,action=goto_table:1\n" +
        "table=0,priority=1000,udp,tp_src=53,action=goto_table:1\n" +
        "table=0,priority=1000,udp,tp_dst=53,action=goto_table:1\n" +
        "table=0,priority=500,ip,nw_dst=192.168.1.8,action=goto_table:2\n" +
        "table=0,priority=500,ip,nw_src=192.168.1.8,action=goto_table:2\n" +
        "table=0,priority=100,dl_dst=ff:ff:ff:ff:ff:ff,action=drop\n" +
        "table=0,priority=100,dl_dst=01:00:5e:00:00:00/ff:ff:ff:80:00:00,action=drop\n" +
        "table=1,priority=499,ip,dl_src=58:b6:23:ed:6f:bd,nw_dst=192.168.1.0/24,action=drop\n" +
        "table=1,priority=499,ip,dl_dst=58:b6:23:ed:6f:bd,nw_src=192.168.1.0/24,action=drop\n" +
        "table=1,priority=0,action=goto_table:2\n" +
        "table=2,priority=1000,dl_src=18:75:32:2e:59:e8,dl_dst=18:75:32:2e:59:e8,action=drop\n" +
        "table=2,priority=1000,dl_dst=ff:ff:ff:ff:ff:ff,dl_src=18:75:32:2e:59:e8,action=output:enp1s0,output:gw0-in-br,output:vp-veth0-in-br,output:wifi0-in-br,output:wifi1-in-br\n" +
        "table=2,priority=1000,arp,dl_dst=18:75:32:2e:59:e8,action=output:enp5s0,output:gw0-in-br\n" +
        "table=2,priority=999,arp,arp_tpa=192.168.1.1,action=output:enp5s0\n" +
        "table=2,priority=1000,udp,tp_src=68,tp_dst=67,action=output:gw0-in-br\n" +
        "table=2,priority=1000,udp,tp_src=67,tp_dst=68,action=output:enp1s0,output:wifi0-in-br,output:wifi1-in-br\n" +
        "table=2,priority=1000,in_port=enp5s0,udp,tp_src=67,tp_dst=68,action=drop\n" +
        "table=2,priority=1000,in_port=enp5s0,udp,tp_src=68,tp_dst=67,action=drop\n" +
        "table=2,priority=1000,in_port=vp-veth0-in-br,arp,arp_spa=192.168.1.1,action=drop\n" +
        "table=2,priority=499,udp,nw_dst=192.168.1.1,tp_dst=53,action=mod_dl_dst:d6:62:92:ee:ce:bf,output:vp-veth0-in-br\n" +
        "table=2,priority=500,in_port=vp-veth0-in-br,udp,nw_dst=192.168.1.1,tp_dst=53,action=output:enp5s0\n" +
        "table=2,priority=500,ip,nw_dst=100.96.0.0/11,action=mod_dl_dst:d6:62:92:ee:ce:bf,output:vp-veth0-in-br\n" +
        "table=2,priority=200,dl_dst=18:75:32:2e:59:e8,action=output:enp5s0\n" +
        "table=2,priority=0,actions=NORMAL";

    @Test
    public void realworld() throws Exception {
        testFlowBunch(TEST_FLOW,
            "" +
                "table=0,priority=1000,ipv6,actions=drop\n" +
                "table=0,priority=1000,ipv6,icmp6,actions=drop\n" +
                "table=0,priority=1000,arp,actions=goto_table:1\n" +
                "table=0,priority=1000,ip,udp,tp_src=68,tp_dst=67,actions=goto_table:1\n" +
                "table=0,priority=1000,ip,udp,tp_src=67,tp_dst=68,actions=goto_table:1\n" +
                "table=0,priority=1000,ip,icmp,actions=goto_table:1\n" +
                "table=0,priority=1000,ip,udp,tp_src=53,actions=goto_table:1\n" +
                "table=0,priority=1000,ip,udp,tp_dst=53,actions=goto_table:1\n" +
                "table=0,priority=500,ip,nw_dst=192.168.1.8,actions=goto_table:2\n" +
                "table=0,priority=500,ip,nw_src=192.168.1.8,actions=goto_table:2\n" +
                "table=0,priority=100,dl_dst=ff:ff:ff:ff:ff:ff,actions=drop\n" +
                "table=0,priority=100,dl_dst=01:00:5e:00:00:00/ff:ff:ff:80:00:00,actions=drop\n" +
                "table=0,priority=0,actions=goto_table:1\n" +
                "table=1,priority=499,dl_src=58:b6:23:ed:6f:bd,ip,nw_dst=192.168.1.0/24,actions=drop\n" +
                "table=1,priority=499,dl_dst=58:b6:23:ed:6f:bd,ip,nw_src=192.168.1.0/24,actions=drop\n" +
                "table=1,priority=0,actions=goto_table:2\n" +
                "table=2,priority=1000,dl_dst=18:75:32:2e:59:e8,dl_src=18:75:32:2e:59:e8,actions=drop\n" +
                "table=2,priority=1000,dl_dst=ff:ff:ff:ff:ff:ff,dl_src=18:75:32:2e:59:e8,actions=output:enp1s0,output:gw0-in-br,output:vp-veth0-in-br,output:wifi0-in-br,output:wifi1-in-br\n" +
                "table=2,priority=1000,dl_dst=18:75:32:2e:59:e8,arp,actions=output:enp5s0,output:gw0-in-br\n" +
                "table=2,priority=1000,ip,udp,tp_src=68,tp_dst=67,actions=output:gw0-in-br\n" +
                "table=2,priority=1000,ip,udp,tp_src=67,tp_dst=68,actions=output:enp1s0,output:wifi0-in-br,output:wifi1-in-br\n" +
                "table=2,priority=1000,in_port=enp5s0,ip,udp,tp_src=67,tp_dst=68,actions=drop\n" +
                "table=2,priority=1000,in_port=enp5s0,ip,udp,tp_src=68,tp_dst=67,actions=drop\n" +
                "table=2,priority=1000,in_port=vp-veth0-in-br,arp,arp_spa=192.168.1.1,actions=drop\n" +
                "table=2,priority=999,arp,arp_tpa=192.168.1.1,actions=output:enp5s0\n" +
                "table=2,priority=500,in_port=vp-veth0-in-br,ip,nw_dst=192.168.1.1,udp,tp_dst=53,actions=output:enp5s0\n" +
                "table=2,priority=500,ip,nw_dst=100.96.0.0/11,actions=mod_dl_dst:d6:62:92:ee:ce:bf,output:vp-veth0-in-br\n" +
                "table=2,priority=499,ip,nw_dst=192.168.1.1,udp,tp_dst=53,actions=mod_dl_dst:d6:62:92:ee:ce:bf,output:vp-veth0-in-br\n" +
                "table=2,priority=200,dl_dst=18:75:32:2e:59:e8,actions=output:enp5s0\n" +
                "table=2,priority=0,actions=normal"
        );
    }
}
