package vproxy.test.cases;

import org.junit.Test;
import vfd.IP;
import vfd.IPv4;
import vfd.IPv6;
import vproxy.util.ByteArray;
import vswitch.packet.*;
import vswitch.util.Consts;
import vswitch.util.MacAddress;

import java.util.Collections;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class TestPacket {
    MacAddress randomMac() {
        byte[] mac = new byte[6];
        new Random().nextBytes(mac);
        return new MacAddress(ByteArray.from(mac));
    }

    IPv4 randomIpv4() {
        byte[] b = new byte[4];
        new Random().nextBytes(b);
        return IP.fromIPv4(b);
    }

    IPv6 randomIpv6() {
        byte[] b = new byte[16];
        new Random().nextBytes(b);
        return IP.fromIPv6(b);
    }

    PacketBytes randomPacket() {
        int len = new Random().nextInt(10) + 10;
        return randomPacket(len);
    }

    PacketBytes randomPacket(int len) {
        PacketBytes b = new PacketBytes();
        byte[] arr = new byte[len];
        new Random().nextBytes(arr);
        b.setBytes(ByteArray.from(arr));
        return b;
    }

    ArpPacket genArp() {
        ArpPacket arp = new ArpPacket();
        arp.setHardwareSize(0xaabb);
        arp.setProtocolType(0xccdd);
        arp.setHardwareSize(6);
        arp.setProtocolSize(4);
        arp.setOpcode(Consts.ARP_PROTOCOL_OPCODE_REQ);
        arp.setSenderMac(randomMac().bytes);
        arp.setSenderIp(ByteArray.from(randomIpv4().getAddress()));
        arp.setTargetMac(randomMac().bytes);
        arp.setTargetIp(ByteArray.from(randomIpv4().getAddress()));
        return arp;
    }

    Ipv4Packet genIpv4() {
        Ipv4Packet ipv4 = new Ipv4Packet();
        ipv4.setVersion(4);
        ipv4.setIhl(6);
        ipv4.setDscp(33);
        ipv4.setEcn(1);
        ipv4.setTotalLength(4 * 6 + 25);
        ipv4.setIdentification(456);
        ipv4.setFlags(2);
        ipv4.setFragmentOffset(4);
        ipv4.setTtl(9);
        ipv4.setProtocol(0xef);
        // cksm
        ipv4.setSrc(randomIpv4());
        ipv4.setDst(randomIpv4());
        ipv4.setOptions(randomPacket(4).getBytes());
        ipv4.setPacket(randomPacket(25));

        ipv4.setHeaderChecksum(ipv4.calculateChecksum());
        return ipv4;
    }

    Ipv6Packet genIpv6() {
        Ipv6Packet ipv6 = new Ipv6Packet();
        ipv6.setVersion(6);
        ipv6.setTrafficClass(28);
        ipv6.setFlowLabel(0x3abcd);
        ipv6.setPayloadLength(75);
        ipv6.setNextHeader(43);
        ipv6.setHopLimit(123);
        ipv6.setSrc(randomIpv6());
        ipv6.setDst(randomIpv6());
        Ipv6Packet.ExtHeader h = new Ipv6Packet.ExtHeader();
        h.setNextHeader(233);
        h.setHdrExtLen(10);
        h.setOther(randomPacket(8 - 2 + 10).getBytes());
        ipv6.setExtHeaders(Collections.singletonList(h));
        ipv6.setPacket(randomPacket(75 - (8 + 10)));
        return ipv6;
    }

    EthernetPacket genEther() {
        EthernetPacket ether = new EthernetPacket();
        ether.setType(Consts.ETHER_TYPE_ARP);
        ether.setDst(randomMac());
        ether.setSrc(randomMac());
        ether.setPacket(genArp());
        return ether;
    }

    <T extends AbstractPacket> void check(T p, Supplier<T> constructor) {
        p.clearRawPacket();
        ByteArray bytes = p.getRawPacket();
        T p2 = constructor.get();
        String err = p2.from(bytes);
        assertNull(err);
        assertEquals(p, p2);
        System.out.println("expect: " + p);
        System.out.println("actual: " + p2);
        p2.clearRawPacket();
        ByteArray bytes2 = p2.getRawPacket();
        assertEquals(bytes, bytes2);
    }

    @Test
    public void arp() {
        ArpPacket arp = genArp();
        check(arp, ArpPacket::new);
    }

    @Test
    public void ipv4ByIcmpExample() {
        ByteArray bytes = ByteArray.from(
            0x45, 0x00, 0x00, 0x3c, 0x7c, 0x57, 0x00, 0x00,
            0x40, 0x01, 0x76, 0xb8, 0xc0, 0xa8, 0x03, 0x60,
            0xc0, 0xa8, 0x03, 0x01,
            0x08, 0x00, 0x4d, 0x5a, 0x00, 0x01, 0x00, 0x01,
            0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
            0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e, 0x6f, 0x70,
            0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x61,
            0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69
        );
        Ipv4Packet ipv4 = new Ipv4Packet();
        String err = ipv4.from(bytes);
        assertNull(err);
        assertTrue(ipv4.getPacket() instanceof IcmpPacket);
        ipv4.getPacket().clearRawPacket();

        ipv4.clearRawPacket();
        ByteArray gen = ipv4.getRawPacket();
        assertEquals(bytes, gen);

        check(ipv4, Ipv4Packet::new);
    }

    @Test
    public void ipv4() {
        Ipv4Packet ipv4 = genIpv4();

        check(ipv4, Ipv4Packet::new);
    }

    @Test
    public void ipv6ByIcmpExample() {
        ByteArray bytes = ByteArray.from(
            0x60, 0x00, 0x00, 0x00, 0x00, 0x28, 0x3a, 0x40,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,

            0x80, 0x00, 0xd4, 0xec, 0x00, 0x01, 0x00, 0x0a,
            0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
            0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e, 0x6f, 0x70,
            0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x61,
            0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69
        );
        Ipv6Packet ipv6 = new Ipv6Packet();
        String err = ipv6.from(bytes);
        assertNull(err);
        assertTrue(ipv6.getPacket() instanceof IcmpPacket);
        ipv6.getPacket().clearRawPacket();

        ipv6.clearRawPacket();
        ByteArray gen = ipv6.getRawPacket();
        assertEquals(bytes, gen);

        check(ipv6, Ipv6Packet::new);
    }

    @Test
    public void ipv6() {
        Ipv6Packet ipv6 = genIpv6();

        check(ipv6, Ipv6Packet::new);
    }

    @Test
    public void ethernet() {
        EthernetPacket ether = new EthernetPacket();
        ether.setType(0x1234);
        ether.setDst(randomMac());
        ether.setSrc(randomMac());
        ether.setPacket(randomPacket());

        check(ether, EthernetPacket::new);
    }

    @Test
    public void ethernetArp() {
        EthernetPacket ether = genEther();

        check(ether, EthernetPacket::new);
    }

    @Test
    public void ethernetIpv4() {
        EthernetPacket ether = new EthernetPacket();
        ether.setType(Consts.ETHER_TYPE_IPv4);
        ether.setDst(randomMac());
        ether.setSrc(randomMac());
        ether.setPacket(genIpv4());

        check(ether, EthernetPacket::new);
    }

    @Test
    public void ethernetIpv6() {
        EthernetPacket ether = new EthernetPacket();
        ether.setType(Consts.ETHER_TYPE_IPv6);
        ether.setDst(randomMac());
        ether.setSrc(randomMac());
        ether.setPacket(genIpv6());

        check(ether, EthernetPacket::new);
    }

    @Test
    public void vxlan() {
        VXLanPacket vxlan = new VXLanPacket();
        vxlan.setFlags(0b01000000);
        vxlan.setVni(1314);
        vxlan.setPacket(genEther());

        check(vxlan, VXLanPacket::new);
    }
}
