package io.vproxy.test.cases;

import io.vproxy.app.app.cmd.handle.resource.SwitchHandle;
import io.vproxy.app.plugin.impl.BasePacketFilter;
import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.Network;
import io.vproxy.base.util.net.SNatIPPortPool;
import io.vproxy.component.secure.SecurityGroup;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vpacket.*;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.PacketFilterHelper;
import io.vproxy.vswitch.RouteTable;
import io.vproxy.vswitch.Switch;
import io.vproxy.vswitch.iface.ProgramIface;
import io.vproxy.vswitch.plugin.FilterResult;
import io.vproxy.vswitch.util.SwitchUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class TestSwitch {
    private Switch sw;
    private EventLoopGroup eventLoopGroup;
    private ProgramIface eth0;
    private ProgramIface eth1;
    private ProgramIface eth2;
    private ProgramIface eth3;

    private ProgramIface eth24;
    private ProgramIface eth25;
    private ProgramIface eth36;
    private ProgramIface eth47;

    @Before
    public void setUp() throws Exception {
        eventLoopGroup = new EventLoopGroup("elg");
        eventLoopGroup.add("el");
        sw = new Switch("test", new IPPort("255.255.255.255:65535"), eventLoopGroup,
            SwitchHandle.MAC_TABLE_TIMEOUT, SwitchHandle.ARP_TABLE_TIMEOUT,
            SecurityGroup.allowAll());
        sw.start();

        sw.addNetwork(1, Network.from("192.168.1.0/24"), Network.from("fd00::1:0/120"), new Annotations());
        sw.addNetwork(2, Network.from("192.168.2.0/24"), Network.from("fd00::2:0/120"), new Annotations());
        sw.addNetwork(3, Network.from("192.168.3.0/24"), Network.from("fd00::3:0/120"), new Annotations());
        sw.addNetwork(4, Network.from("192.168.4.0/24"), Network.from("fd00::4:0/120"), new Annotations());

        eth0 = sw.addProgramIface("eth0", 1);
        eth1 = sw.addProgramIface("eth1", 1);
        eth2 = sw.addProgramIface("eth2", 1);
        eth3 = sw.addProgramIface("eth3", 1);

        eth24 = sw.addProgramIface("eth24", 2);
        eth25 = sw.addProgramIface("eth25", 2);
        eth36 = sw.addProgramIface("eth36", 3);
        eth47 = sw.addProgramIface("eth47", 4);
    }

    @After
    public void tearDown() throws Exception {
        if (sw != null) {
            sw.stop();
        }
        if (eventLoopGroup != null) {
            try {
                eventLoopGroup.remove("el");
            } catch (Exception ignore) {
            }
        }
    }

    private ProgramIface.ReceivedPacket waitForPacket(ProgramIface iface) {
        ProgramIface.ReceivedPacket ret;
        while ((ret = iface.poll()) == null) {
            try {
                //noinspection BusyWait
                Thread.sleep(10);
            } catch (InterruptedException ignore) {
            }
        }
        return ret;
    }

    @Test
    public void broadcast() throws Exception {
        var e = new EthernetPacket();
        e.setSrc(new MacAddress("aa:bb:cc:dd:ee:00"));
        e.setDst(new MacAddress("ff:ff:ff:ff:ff:ff"));
        e.setType(0xffff);
        e.setPacket(new PacketBytes(ByteArray.from("hello")));

        eth0.injectPacket(e);

        assertEquals(waitForPacket(eth1).pkt, e);
        assertEquals(waitForPacket(eth2).pkt, e);
        assertEquals(waitForPacket(eth3).pkt, e);
        assertNull(eth24.poll());
        assertNull(eth25.poll());
        assertNull(eth36.poll());
        assertNull(eth47.poll());

        var net1 = sw.getNetwork(1);
        assertSame(net1.macTable.lookup(new MacAddress("aa:bb:cc:dd:ee:00")), eth0);
    }

    private EthernetPacket buildArpRequest(String srcMac, String srcIP, String dstIP) {
        var e = new EthernetPacket();
        e.setSrc(new MacAddress(srcMac));
        e.setDst(MacAddress.BROADCAST);
        e.setType(Consts.ETHER_TYPE_ARP);
        var arp = new ArpPacket();
        e.setPacket(arp);
        arp.setHardwareType(Consts.ARP_HARDWARE_TYPE_ETHER);
        arp.setProtocolType(Consts.ARP_PROTOCOL_TYPE_IP);
        arp.setHardwareSize(6);
        arp.setProtocolSize(4);
        arp.setOpcode(Consts.ARP_PROTOCOL_OPCODE_REQ);
        arp.setSenderMac(new MacAddress("aa:bb:cc:dd:ee:00").bytes);
        arp.setSenderIp(IP.from(srcIP).bytes);
        arp.setTargetMac(MacAddress.ZERO.bytes);
        arp.setTargetIp(IP.from(dstIP).bytes);
        return e;
    }

    @Test
    public void floodArp() throws Exception {
        var e = buildArpRequest("aa:bb:cc:dd:ee:00", "192.168.1.0", "192.168.1.1");
        e.setDst(new MacAddress("aa:bb:cc:dd:ee:01"));

        eth0.injectPacket(e);

        assertEquals(waitForPacket(eth1).pkt, e);
        assertEquals(waitForPacket(eth2).pkt, e);
        assertEquals(waitForPacket(eth3).pkt, e);
        assertNull(eth24.poll());
        assertNull(eth25.poll());
        assertNull(eth36.poll());
        assertNull(eth47.poll());

        var net1 = sw.getNetwork(1);
        assertSame(net1.macTable.lookup(new MacAddress("aa:bb:cc:dd:ee:00")), eth0);
        assertEquals(new MacAddress("aa:bb:cc:dd:ee:00"), net1.arpTable.lookup(IP.from("192.168.1.0")));
    }

    @Test
    public void unicast() throws Exception {
        var net1 = sw.getNetwork(1);
        net1.macTable.record(new MacAddress("aa:bb:cc:dd:ee:01"), eth1);

        var e = new EthernetPacket();
        e.setSrc(new MacAddress("aa:bb:cc:dd:ee:00"));
        e.setDst(new MacAddress("aa:bb:cc:dd:ee:01"));
        e.setType(0xffff);
        e.setPacket(new PacketBytes(ByteArray.from("hello")));

        eth0.injectPacket(e);

        assertEquals(waitForPacket(eth1).pkt, e);
        assertNull(eth2.poll());
        assertNull(eth3.poll());
        assertNull(eth24.poll());
        assertNull(eth25.poll());
        assertNull(eth36.poll());
        assertNull(eth47.poll());

        assertSame(net1.macTable.lookup(new MacAddress("aa:bb:cc:dd:ee:00")), eth0);
    }

    @Test
    public void arpRespond() throws Exception {
        var net1 = sw.getNetwork(1);
        net1.ips.add(IP.from("192.168.1.198"), new MacAddress("00:00:00:00:00:01"), new Annotations());

        var e = buildArpRequest("aa:bb:cc:dd:ee:02", "192.168.1.2", "192.168.1.198");
        eth2.injectPacket(e);

        assertEquals(waitForPacket(eth0).pkt, e);
        assertEquals(waitForPacket(eth1).pkt, e);
        assertEquals(waitForPacket(eth3).pkt, e);
        assertNull(eth24.poll());
        assertNull(eth25.poll());
        assertNull(eth36.poll());
        assertNull(eth47.poll());

        var pkt = waitForPacket(eth2).pkt;
        var arp = (ArpPacket) pkt.getPacket();
        assertEquals(arp.getSenderMac(), new MacAddress("00:00:00:00:00:01").bytes);
    }

    private EthernetPacket buildNeighborSolicitation(String srcMac, String srcIP, String dstIP) {
        var e = new EthernetPacket();
        e.setSrc(new MacAddress(srcMac));
        e.setDst(MacAddress.BROADCAST);
        e.setType(Consts.ETHER_TYPE_IPv6);
        var ip = SwitchUtils.buildNeighborSolicitationPacket(
            IP.fromIPv6(dstIP), new MacAddress(srcMac), IP.fromIPv6(srcIP));
        e.setPacket(ip);
        return e;
    }

    @Test
    public void ndpRespond() throws Exception {
        var net1 = sw.getNetwork(1);
        net1.ips.add(IP.from("fd00::1:c6"), new MacAddress("00:00:00:00:00:01"), new Annotations());

        var e = buildNeighborSolicitation("aa:bb:cc:dd:ee:02", "fd00::1:2", "fd00::1:c6");
        eth2.injectPacket(e);

        var pkt = waitForPacket(eth2).pkt;

        assertEquals(waitForPacket(eth0).pkt, e);
        assertEquals(waitForPacket(eth1).pkt, e);
        assertEquals(waitForPacket(eth3).pkt, e);
        assertNull(eth24.poll());
        assertNull(eth25.poll());
        assertNull(eth36.poll());
        assertNull(eth47.poll());

        var v6 = (Ipv6Packet) pkt.getPacket();
        var icmp = (IcmpPacket) v6.getPacket();
        assertTrue(icmp.isIpv6());
        assertEquals(icmp.getType(), Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Advertisement);
        var p = SwitchUtils.buildNeighborAdvertisementPacket(new MacAddress("00:00:00:00:00:01"),
            IP.fromIPv6("fd00::1:c6"), IP.fromIPv6("fd00::1:2"));
        assertEquals(p, v6);
    }

    private EthernetPacket buildPing(String srcMac, String srcIP, String dstMac, String dstIp) {
        var src = IP.from(srcIP);
        IcmpPacket icmpPacket;
        if (src instanceof IPv4) {
            icmpPacket = new IcmpPacket(false);
            icmpPacket.setType(Consts.ICMP_PROTOCOL_TYPE_ECHO_REQ);
            icmpPacket.setCode(1234);
            icmpPacket.setOther(ByteArray.from("abcd"));
        } else {
            icmpPacket = new IcmpPacket(true);
            icmpPacket.setType(Consts.ICMPv6_PROTOCOL_TYPE_ECHO_REQ);
            icmpPacket.setCode(1234);
            icmpPacket.setOther(ByteArray.from("abcd"));
        }
        return SwitchUtils.buildEtherIpIcmpPacket(
            new MacAddress(dstMac), new MacAddress(srcMac),
            IP.from(srcIP), IP.from(dstIp),
            icmpPacket);
    }

    @Test
    public void pingRespondV4() throws Exception {
        var net1 = sw.getNetwork(1);
        net1.ips.add(IP.from("192.168.1.198"), new MacAddress("00:00:00:00:00:01"), new Annotations());
        net1.arpTable.record(new MacAddress("aa:bb:cc:dd:ee:02"), IP.from("192.168.1.2"));

        var e = buildPing("aa:bb:cc:dd:ee:02", "192.168.1.2", "00:00:00:00:00:01", "192.168.1.198");
        eth2.injectPacket(e);

        var pkt = waitForPacket(eth2).pkt;

        assertNull(eth0.poll());
        assertNull(eth1.poll());
        assertNull(eth3.poll());
        assertNull(eth24.poll());
        assertNull(eth25.poll());
        assertNull(eth36.poll());
        assertNull(eth47.poll());

        var ip = (AbstractIpPacket) pkt.getPacket();
        var icmp = (IcmpPacket) ip.getPacket();
        assertEquals(Consts.ICMP_PROTOCOL_TYPE_ECHO_RESP, icmp.getType());
        assertEquals(ByteArray.from("abcd"), icmp.getOther());
    }

    @Test
    public void pingRespondV6() throws Exception {
        var net1 = sw.getNetwork(1);
        net1.ips.add(IP.from("fd00::1:c6"), new MacAddress("00:00:00:00:00:01"), new Annotations());
        net1.arpTable.record(new MacAddress("aa:bb:cc:dd:ee:02"), IP.from("fd00::1:2"));

        var e = buildPing("aa:bb:cc:dd:ee:02", "fd00::1:2", "00:00:00:00:00:01", "fd00::1:c6");
        eth2.injectPacket(e);

        var pkt = waitForPacket(eth2).pkt;

        assertNull(eth0.poll());
        assertNull(eth1.poll());
        assertNull(eth3.poll());
        assertNull(eth24.poll());
        assertNull(eth25.poll());
        assertNull(eth36.poll());
        assertNull(eth47.poll());

        var ip = (AbstractIpPacket) pkt.getPacket();
        var icmp = (IcmpPacket) ip.getPacket();
        assertEquals(Consts.ICMPv6_PROTOCOL_TYPE_ECHO_RESP, icmp.getType());
        assertEquals(ByteArray.from("abcd"), icmp.getOther());
    }

    @Test
    public void routePing() throws Exception {
        var net1 = sw.getNetwork(1);
        net1.ips.add(IP.from("192.168.1.198"), new MacAddress("00:00:00:00:00:01"), new Annotations());
        net1.arpTable.record(new MacAddress("aa:bb:cc:dd:ee:02"), IP.from("192.168.1.2"));
        net1.routeTable.addRule(new RouteTable.RouteRule("to-2", Network.from("192.168.2.0/24"), 2));

        var net2 = sw.getNetwork(2);
        net2.ips.add(IP.from("192.168.2.198"), new MacAddress("00:00:00:00:00:02"), new Annotations());
        net2.routeTable.addRule(new RouteTable.RouteRule("to-1", Network.from("192.168.1.0/24"), 1));

        var e = buildPing("aa:bb:cc:dd:ee:02", "192.168.1.2", "00:00:00:00:00:01", "192.168.2.198");
        eth2.injectPacket(e);

        var pkt = waitForPacket(eth2).pkt;

        assertNull(eth0.poll());
        assertNull(eth1.poll());
        assertNull(eth3.poll());
        assertNull(eth24.poll());
        assertNull(eth25.poll());
        assertNull(eth36.poll());
        assertNull(eth47.poll());

        var ip = (AbstractIpPacket) pkt.getPacket();
        var icmp = (IcmpPacket) ip.getPacket();
        assertEquals(Consts.ICMP_PROTOCOL_TYPE_ECHO_RESP, icmp.getType());
        assertEquals(ByteArray.from("abcd"), icmp.getOther());
    }

    @Test
    public void routePingV6() throws Exception {
        var net1 = sw.getNetwork(1);
        net1.ips.add(IP.from("fd00::1:c6"), new MacAddress("00:00:00:00:00:01"), new Annotations());
        net1.arpTable.record(new MacAddress("aa:bb:cc:dd:ee:02"), IP.from("fd00::1:2"));
        net1.routeTable.addRule(new RouteTable.RouteRule("to-2", Network.from("fd00::2:0/120"), 2));

        var net2 = sw.getNetwork(2);
        net2.ips.add(IP.from("fd00::2:c6"), new MacAddress("00:00:00:00:00:02"), new Annotations());
        net2.routeTable.addRule(new RouteTable.RouteRule("to-1", Network.from("fd00::1:0/120"), 1));

        var e = buildPing("aa:bb:cc:dd:ee:02", "fd00::1:2", "00:00:00:00:00:01", "fd00::2:c6");
        eth2.injectPacket(e);

        var pkt = waitForPacket(eth2).pkt;

        assertNull(eth0.poll());
        assertNull(eth1.poll());
        assertNull(eth3.poll());
        assertNull(eth24.poll());
        assertNull(eth25.poll());
        assertNull(eth36.poll());
        assertNull(eth47.poll());

        var ip = (AbstractIpPacket) pkt.getPacket();
        var icmp = (IcmpPacket) ip.getPacket();
        assertEquals(Consts.ICMPv6_PROTOCOL_TYPE_ECHO_RESP, icmp.getType());
        assertEquals(ByteArray.from("abcd"), icmp.getOther());
    }

    @Test
    public void routePingVia() throws Exception {
        var net1 = sw.getNetwork(1);
        net1.ips.add(IP.from("192.168.1.198"), new MacAddress("00:00:00:00:00:01"), new Annotations());
        net1.arpTable.record(new MacAddress("aa:bb:cc:dd:ee:01"), IP.from("192.168.1.1"));
        net1.arpTable.record(new MacAddress("aa:bb:cc:dd:ee:02"), IP.from("192.168.1.2"));
        net1.macTable.record(new MacAddress("aa:bb:cc:dd:ee:01"), eth1);
        net1.routeTable.addRule(new RouteTable.RouteRule("gw", Network.from("10.0.1.0/24"), IP.from("192.168.1.1")));

        var e = buildPing("aa:bb:cc:dd:ee:02", "192.168.1.2", "00:00:00:00:00:01", "10.0.1.2");
        eth2.injectPacket(e);

        var pkt = waitForPacket(eth1).pkt;

        assertNull(eth0.poll());
        assertNull(eth2.poll());
        assertNull(eth3.poll());
        assertNull(eth24.poll());
        assertNull(eth25.poll());
        assertNull(eth36.poll());
        assertNull(eth47.poll());

        var ip = (AbstractIpPacket) pkt.getPacket();
        var icmp = (IcmpPacket) ip.getPacket();
        assertEquals(Consts.ICMP_PROTOCOL_TYPE_ECHO_REQ, icmp.getType());
        assertEquals(ByteArray.from("abcd"), icmp.getOther());
    }

    @Test
    public void routePingV6Via() throws Exception {
        var net1 = sw.getNetwork(1);
        net1.ips.add(IP.from("fd00::1:c6"), new MacAddress("00:00:00:00:00:01"), new Annotations());
        net1.arpTable.record(new MacAddress("aa:bb:cc:dd:ee:01"), IP.from("fd00::1:1"));
        net1.arpTable.record(new MacAddress("aa:bb:cc:dd:ee:02"), IP.from("fd00::1:2"));
        net1.macTable.record(new MacAddress("aa:bb:cc:dd:ee:01"), eth1);
        net1.routeTable.addRule(new RouteTable.RouteRule("gw", Network.from("2000::/120"), IP.from("fd00::1:1")));

        var e = buildPing("aa:bb:cc:dd:ee:02", "fd00::1:2", "00:00:00:00:00:01", "2000::ab");
        eth2.injectPacket(e);

        var pkt = waitForPacket(eth1).pkt;

        assertNull(eth0.poll());
        assertNull(eth2.poll());
        assertNull(eth3.poll());
        assertNull(eth24.poll());
        assertNull(eth25.poll());
        assertNull(eth36.poll());
        assertNull(eth47.poll());

        var ip = (AbstractIpPacket) pkt.getPacket();
        var icmp = (IcmpPacket) ip.getPacket();
        assertEquals(Consts.ICMPv6_PROTOCOL_TYPE_ECHO_REQ, icmp.getType());
        assertEquals(ByteArray.from("abcd"), icmp.getOther());
    }

    private EthernetPacket buildTcp(String srcMac, String srcIP, int sport, String dstMac, String dstIp, int dport, long seq) {
        return buildTcp(srcMac, srcIP, sport, dstMac, dstIp, dport, seq, Consts.TCP_FLAGS_SYN);
    }

    private EthernetPacket buildTcp(String srcMac, String srcIP, int sport, String dstMac, String dstIp, int dport, long seq, int flags) {
        var tcpPkt = new TcpPacket();
        tcpPkt.setSrcPort(sport);
        tcpPkt.setDstPort(dport);
        tcpPkt.setSeqNum(seq);
        tcpPkt.setAckNum(0);
        tcpPkt.setDataOffset(5);
        tcpPkt.setFlags(flags);
        tcpPkt.setWindow(1024);
        tcpPkt.setUrgentPointer(0);
        tcpPkt.setOptions(new ArrayList<>());
        tcpPkt.setData(ByteArray.allocate(0));

        if (IP.isIpv4(srcIP)) {
            tcpPkt.buildIPv4TcpPacket(new Ipv4Packet() {{
                setSrc(IP.fromIPv4(srcIP));
                setDst(IP.fromIPv4(dstIp));
            }}, AbstractPacket.FLAG_CHECKSUM_UNNECESSARY);
        } else {
            tcpPkt.buildIPv6TcpPacket(new Ipv6Packet() {{
                setSrc(IP.fromIPv6(srcIP));
                setDst(IP.fromIPv6(dstIp));
            }}, AbstractPacket.FLAG_CHECKSUM_UNNECESSARY);
        }

        return SwitchUtils.buildEtherIpPacket(new MacAddress(dstMac), new MacAddress(srcMac),
            SwitchUtils.buildIpPacket(IP.from(srcIP), IP.from(dstIp), Consts.IP_PROTOCOL_TCP,
                tcpPkt));
    }

    @Test
    public void tcpRst() throws Exception {
        var net1 = sw.getNetwork(1);
        net1.ips.add(IP.from("192.168.1.198"), new MacAddress("00:00:00:00:00:01"), new Annotations());
        net1.arpTable.record(new MacAddress("aa:bb:cc:dd:ee:00"), IP.from("192.168.1.0"));
        net1.macTable.record(new MacAddress("aa:bb:cc:dd:ee:00"), eth0);

        var seq = 158983329767L;
        var e = buildTcp("aa:bb:cc:dd:ee:00", "192.168.1.0", 45678, "00:00:00:00:00:01", "192.168.1.198", 8080, seq);
        eth0.injectPacket(e);

        var pkt = waitForPacket(eth0).pkt;

        assertNull(eth1.poll());
        assertNull(eth2.poll());
        assertNull(eth3.poll());
        assertNull(eth24.poll());
        assertNull(eth25.poll());
        assertNull(eth36.poll());
        assertNull(eth47.poll());

        var ip = (AbstractIpPacket) pkt.getPacket();
        var tcp = (TcpPacket) ip.getPacket();
        assertEquals(Consts.TCP_FLAGS_RST | Consts.TCP_FLAGS_ACK, tcp.getFlags());
        assertEquals(seq + 1 /*+ 1 to ack the syn packet*/, tcp.getAckNum());
    }

    @Test
    public void tcpRstV6() throws Exception {
        var net1 = sw.getNetwork(1);
        net1.ips.add(IP.from("fd00::1:c6"), new MacAddress("00:00:00:00:00:01"), new Annotations());
        net1.arpTable.record(new MacAddress("aa:bb:cc:dd:ee:00"), IP.from("fd00::1:0"));
        net1.macTable.record(new MacAddress("aa:bb:cc:dd:ee:00"), eth0);

        var seq = 894729725211L;
        var e = buildTcp("aa:bb:cc:dd:ee:00", "fd00::1:0", 34567, "00:00:00:00:00:01", "fd00::1:c6", 443, seq);
        eth0.injectPacket(e);

        var pkt = waitForPacket(eth0).pkt;

        assertNull(eth1.poll());
        assertNull(eth2.poll());
        assertNull(eth3.poll());
        assertNull(eth24.poll());
        assertNull(eth25.poll());
        assertNull(eth36.poll());
        assertNull(eth47.poll());

        var ip = (AbstractIpPacket) pkt.getPacket();
        var tcp = (TcpPacket) ip.getPacket();
        assertEquals(Consts.TCP_FLAGS_RST | Consts.TCP_FLAGS_ACK, tcp.getFlags());
        assertEquals(seq + 1 /*+ 1 to ack the syn packet*/, tcp.getAckNum());
    }

    private EthernetPacket buildUdp(String srcMac, String srcIP, int sport, String dstMac, String dstIp, int dport) {
        var udpPkt = new UdpPacket();
        udpPkt.setSrcPort(1234);
        udpPkt.setDstPort(5678);
        udpPkt.setLength(8);
        udpPkt.setChecksum(0);
        udpPkt.setData(new PacketBytes(ByteArray.allocate(0)));

        if (IP.isIpv4(srcIP)) {
            udpPkt.buildIPv4UdpPacket(new Ipv4Packet() {{
                setSrc(IP.fromIPv4(srcIP));
                setDst(IP.fromIPv4(dstIp));
            }}, AbstractPacket.FLAG_CHECKSUM_UNNECESSARY);
        } else {
            udpPkt.buildIPv6UdpPacket(new Ipv6Packet() {{
                setSrc(IP.fromIPv6(srcIP));
                setDst(IP.fromIPv6(dstIp));
            }}, AbstractPacket.FLAG_CHECKSUM_UNNECESSARY);
        }

        return SwitchUtils.buildEtherIpPacket(new MacAddress(dstMac), new MacAddress(srcMac),
            SwitchUtils.buildIpPacket(IP.from(srcIP), IP.from(dstIp), Consts.IP_PROTOCOL_TCP,
                udpPkt));
    }

    @Test
    public void udpUnreachable() throws Exception {
        var net1 = sw.getNetwork(1);
        net1.ips.add(IP.from("192.168.1.198"), new MacAddress("00:00:00:00:00:01"), new Annotations());
        net1.arpTable.record(new MacAddress("aa:bb:cc:dd:ee:00"), IP.from("192.168.1.0"));
        net1.macTable.record(new MacAddress("aa:bb:cc:dd:ee:00"), eth0);

        var e = buildUdp("aa:bb:cc:dd:ee:00", "192.168.1.0", 34567, "00:00:00:00:00:01", "192.168.1.198", 443);
        eth0.injectPacket(e);

        var pkt = waitForPacket(eth0).pkt;

        assertNull(eth1.poll());
        assertNull(eth2.poll());
        assertNull(eth3.poll());
        assertNull(eth24.poll());
        assertNull(eth25.poll());
        assertNull(eth36.poll());
        assertNull(eth47.poll());

        var ip = (AbstractIpPacket) pkt.getPacket();
        var icmp = (IcmpPacket) ip.getPacket();
        assertEquals(Consts.ICMP_PROTOCOL_TYPE_DEST_UNREACHABLE, icmp.getType());
        assertEquals(Consts.ICMP_PROTOCOL_CODE_PORT_UNREACHABLE, icmp.getCode());
    }

    @Test
    public void udpUnreachableV6() throws Exception {
        var net1 = sw.getNetwork(1);
        net1.ips.add(IP.from("fd00::1:c6"), new MacAddress("00:00:00:00:00:01"), new Annotations());
        net1.arpTable.record(new MacAddress("aa:bb:cc:dd:ee:00"), IP.from("fd00::1:0"));
        net1.macTable.record(new MacAddress("aa:bb:cc:dd:ee:00"), eth0);

        var e = buildUdp("aa:bb:cc:dd:ee:00", "fd00::1:0", 34567, "00:00:00:00:00:01", "fd00::1:c6", 443);
        eth0.injectPacket(e);

        var pkt = waitForPacket(eth0).pkt;

        assertNull(eth1.poll());
        assertNull(eth2.poll());
        assertNull(eth3.poll());
        assertNull(eth24.poll());
        assertNull(eth25.poll());
        assertNull(eth36.poll());
        assertNull(eth47.poll());

        var ip = (AbstractIpPacket) pkt.getPacket();
        var icmp = (IcmpPacket) ip.getPacket();
        assertEquals(Consts.ICMPv6_PROTOCOL_TYPE_DEST_UNREACHABLE, icmp.getType());
        assertEquals(Consts.ICMPv6_PROTOCOL_CODE_PORT_UNREACHABLE, icmp.getCode());
    }

    @Test
    public void snat() throws Exception {
        sw.addIfaceWatcher(new BasePacketFilter() {
            private final SNatIPPortPool snatIPPortPool = new SNatIPPortPool("192.168.1.198:3000-4000");

            @Override
            protected FilterResult handleIngress(PacketFilterHelper helper, PacketBuffer pkb) {
                if (pkb.devin == null) {
                    return FilterResult.PASS;
                }
                if (pkb.devin.name().equals("program:eth0")) {
                    if (!helper.executeSNat(pkb, snatIPPortPool)) {
                        return FilterResult.DROP;
                    }
                    return FilterResult.L4_TX;
                } else if (pkb.devin.name().equals("program:eth1")) {
                    if (!helper.executeNat(pkb)) {
                        return FilterResult.DROP;
                    }
                    return FilterResult.L4_TX;
                }
                return FilterResult.PASS;
            }
        });

        var net1 = sw.getNetwork(1);
        net1.ips.add(IP.from("192.168.1.198"), new MacAddress("00:00:00:00:00:01"), new Annotations());
        net1.arpTable.record(new MacAddress("aa:bb:cc:dd:ee:00"), IP.from("192.168.1.0"));
        net1.macTable.record(new MacAddress("aa:bb:cc:dd:ee:00"), eth0);
        net1.arpTable.record(new MacAddress("aa:bb:cc:dd:ee:01"), IP.from("192.168.1.1"));
        net1.macTable.record(new MacAddress("aa:bb:cc:dd:ee:01"), eth1);

        var e = buildTcp("aa:bb:cc:dd:ee:00", "192.168.1.0", 34567, "aa:bb:cc:dd:ee:01", "192.168.1.1", 443, 12345678, Consts.TCP_FLAGS_SYN);
        var x = new EthernetPacket();
        x.from(new PacketDataBuffer(e.getRawPacket(0)), true);
        eth0.injectPacket(x);

        var pkt = waitForPacket(eth1).pkt;

        assertNull(eth0.poll());
        assertNull(eth2.poll());
        assertNull(eth3.poll());
        assertNull(eth24.poll());
        assertNull(eth25.poll());
        assertNull(eth36.poll());
        assertNull(eth47.poll());

        assertNull(pkt.getPacketBytes()); // requires mss update, so it's null
        assertEquals(new MacAddress("aa:bb:cc:dd:ee:01"), pkt.getDst());
        assertEquals(new MacAddress("00:00:00:00:00:01"), pkt.getSrc());
        var ip = (Ipv4Packet) pkt.getPacket();
        assertEquals(IP.from("192.168.1.198"), ip.getSrc());
        assertEquals(IP.from("192.168.1.1"), ip.getDst());
        var tcp = (TcpPacket) ip.getPacket();
        assertTrue(3000 <= tcp.getSrcPort() && tcp.getSrcPort() <= 4000);
        assertEquals(443, tcp.getDstPort());

        e = buildTcp("aa:bb:cc:dd:ee:01", "192.168.1.1", 443, "00:00:00:00:00:01", "192.168.1.198", tcp.getSrcPort(), 12345678, Consts.TCP_FLAGS_PSH);
        x.from(new PacketDataBuffer(e.getRawPacket(0)), true);
        eth1.injectPacket(x);

        pkt = waitForPacket(eth0).pkt;

        assertNull(eth1.poll());
        assertNull(eth2.poll());
        assertNull(eth3.poll());
        assertNull(eth24.poll());
        assertNull(eth25.poll());
        assertNull(eth36.poll());
        assertNull(eth47.poll());

        assertNotNull(pkt.getPacketBytes());
        assertEquals(new MacAddress("aa:bb:cc:dd:ee:00"), pkt.getDst());
        assertEquals(new MacAddress("00:00:00:00:00:01"), pkt.getSrc());
        ip = (Ipv4Packet) pkt.getPacket();
        assertEquals(IP.from("192.168.1.1"), ip.getSrc());
        assertEquals(IP.from("192.168.1.0"), ip.getDst());
        tcp = (TcpPacket) ip.getPacket();
        assertEquals(443, tcp.getSrcPort());
        assertEquals(34567, tcp.getDstPort());
    }
}
