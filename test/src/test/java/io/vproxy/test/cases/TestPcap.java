package io.vproxy.test.cases;

import io.vproxy.base.util.Consts;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.*;
import org.junit.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestPcap {
    private void check(Function<PcapPacket, AbstractPacket> getIP, List<PcapPacket> packets, List<PktCheck> check) {
        assertEquals(check.size(), packets.size());
        for (int i = 0; i < check.size(); ++i) {
            var p = packets.get(i);
            var c = check.get(i);

            var ip = (AbstractIpPacket) getIP.apply(p);
            var tcp = (TcpPacket) ip.getPacket();

            assertEquals(STR."src ip idx=\{i}", c.src.getAddress(), ip.getSrc());
            assertEquals(STR."dst ip idx=\{i}", c.dst.getAddress(), ip.getDst());
            assertEquals(STR."src port idx=\{i}", c.src.getPort(), tcp.getSrcPort());
            assertEquals(STR."dst port idx=\{i}", c.dst.getPort(), tcp.getDstPort());
        }
    }

    private static class PktCheck {
        final IPPort src;
        final IPPort dst;
        final int flags;

        private PktCheck(IPPort src, IPPort dst, int flags) {
            this.src = src;
            this.dst = dst;
            this.flags = flags;
        }
    }

    @Test
    public void ether() {
        var parser = new PcapParser(TestPcap.class.getResourceAsStream("/pcap/cap-ether.pcap"));
        var all = parser.parseAll();
        var addr1 = new IPPort("10.111.0.2", 44658);
        var addr2 = new IPPort("39.156.66.10", 80);
        check(p -> ((EthernetPacket) p.getPacket()).getPacket(), all, List.of(
            new PktCheck(addr1, addr2, Consts.TCP_FLAGS_SYN),
            new PktCheck(addr2, addr1, Consts.TCP_FLAGS_SYN | Consts.TCP_FLAGS_ACK),
            new PktCheck(addr1, addr2, Consts.TCP_FLAGS_ACK),
            new PktCheck(addr1, addr2, Consts.TCP_FLAGS_PSH | Consts.TCP_FLAGS_ACK),
            new PktCheck(addr2, addr1, Consts.TCP_FLAGS_ACK),
            new PktCheck(addr2, addr1, Consts.TCP_FLAGS_PSH | Consts.TCP_FLAGS_ACK),
            new PktCheck(addr1, addr2, Consts.TCP_FLAGS_ACK),
            new PktCheck(addr2, addr1, Consts.TCP_FLAGS_PSH | Consts.TCP_FLAGS_ACK),
            new PktCheck(addr1, addr2, Consts.TCP_FLAGS_ACK),
            new PktCheck(addr1, addr2, Consts.TCP_FLAGS_FIN | Consts.TCP_FLAGS_ACK),
            new PktCheck(addr2, addr1, Consts.TCP_FLAGS_ACK),
            new PktCheck(addr2, addr1, Consts.TCP_FLAGS_FIN | Consts.TCP_FLAGS_ACK),
            new PktCheck(addr1, addr2, Consts.TCP_FLAGS_ACK)
        ));
    }

    @Test
    public void linuxCooked() {
        var parser = new PcapParser(TestPcap.class.getResourceAsStream("/pcap/cap-linux-cooked.pcap"));
        var all = parser.parseAll();
        var addr1 = new IPPort("10.111.0.2", 44656);
        var addr2 = new IPPort("39.156.66.10", 80);
        check(p -> ((LinuxCookedPacket) p.getPacket()).getPayload(), all, List.of(
            new PktCheck(addr1, addr2, Consts.TCP_FLAGS_SYN),
            new PktCheck(addr2, addr1, Consts.TCP_FLAGS_SYN | Consts.TCP_FLAGS_ACK),
            new PktCheck(addr1, addr2, Consts.TCP_FLAGS_ACK),
            new PktCheck(addr1, addr2, Consts.TCP_FLAGS_PSH | Consts.TCP_FLAGS_ACK),
            new PktCheck(addr2, addr1, Consts.TCP_FLAGS_ACK),
            new PktCheck(addr2, addr1, Consts.TCP_FLAGS_PSH | Consts.TCP_FLAGS_ACK),
            new PktCheck(addr1, addr2, Consts.TCP_FLAGS_ACK),
            new PktCheck(addr2, addr1, Consts.TCP_FLAGS_PSH | Consts.TCP_FLAGS_ACK),
            new PktCheck(addr1, addr2, Consts.TCP_FLAGS_ACK),
            new PktCheck(addr1, addr2, Consts.TCP_FLAGS_FIN | Consts.TCP_FLAGS_ACK),
            new PktCheck(addr2, addr1, Consts.TCP_FLAGS_ACK),
            new PktCheck(addr2, addr1, Consts.TCP_FLAGS_FIN | Consts.TCP_FLAGS_ACK),
            new PktCheck(addr1, addr2, Consts.TCP_FLAGS_ACK),
            new PktCheck(addr2, new IPPort("10.111.0.2", 44654), Consts.TCP_FLAGS_RST)
        ));
    }

    @Test
    public void bsd() {
        var parser = new PcapParser(TestPcap.class.getResourceAsStream("/pcap/cap-bsd-loopback-encap.pcap"));
        var all = parser.parseAll();
        assertEquals(4, all.size());
        var pkts = all.stream().map(p -> (Ipv4Packet) ((BSDLoopbackEncapsulation) p.getPacket()).getPayload()).toList();
        assertEquals(IP.from("10.99.88.1"), pkts.get(0).getSrc());
        assertEquals(IP.from("10.99.88.199"), pkts.get(0).getDst());
        assertEquals(IP.from("10.99.88.199"), pkts.get(1).getSrc());
        assertEquals(IP.from("10.99.88.1"), pkts.get(1).getDst());
        assertEquals(IP.from("10.99.88.1"), pkts.get(2).getSrc());
        assertEquals(IP.from("10.99.88.199"), pkts.get(2).getDst());
        assertEquals(IP.from("10.99.88.199"), pkts.get(3).getSrc());
        assertEquals(IP.from("10.99.88.1"), pkts.get(3).getDst());
        for (var pkt : pkts) {
            assertTrue(pkt.getPacket() instanceof IcmpPacket);
        }
    }
}
