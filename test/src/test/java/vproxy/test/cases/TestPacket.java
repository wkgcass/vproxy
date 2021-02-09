package vproxy.test.cases;

import org.junit.Test;
import vfd.IP;
import vfd.IPv4;
import vfd.IPv6;
import vfd.MacAddress;
import vpacket.*;
import vproxybase.util.ByteArray;
import vproxybase.util.Consts;
import vproxybase.util.Utils;

import java.util.Collections;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class TestPacket {
    MacAddress randomMac() {
        byte[] mac = Utils.allocateByteArrayInitZero(6);
        new Random().nextBytes(mac);
        return new MacAddress(ByteArray.from(mac));
    }

    IPv4 randomIpv4() {
        byte[] b = Utils.allocateByteArrayInitZero(4);
        new Random().nextBytes(b);
        return IP.fromIPv4(b);
    }

    IPv6 randomIpv6() {
        byte[] b = Utils.allocateByteArrayInitZero(16);
        new Random().nextBytes(b);
        return IP.fromIPv6(b);
    }

    PacketBytes randomPacket() {
        int len = new Random().nextInt(10) + 10;
        return randomPacket(len);
    }

    PacketBytes randomPacket(int len) {
        PacketBytes b = new PacketBytes();
        byte[] arr = Utils.allocateByteArray(len);
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

    @Test
    public void tcpIpv4SynExample() {
        ByteArray bytes = ByteArray.from(
            0xcc, 0x70, 0xed, 0xc4, 0xe4, 0xf9, 0xf8, 0xff, 0xc2, 0x07, 0x89, 0x6e, 0x08, 0x00, 0x45, 0x00,
            0x00, 0x40, 0x00, 0x00, 0x40, 0x00, 0x40, 0x06, 0x87, 0xe4, 0x0a, 0xf2, 0xc2, 0x70, 0xb4, 0x65,
            0x31, 0x0c, 0xf5, 0x68, 0x01, 0xbb, 0xea, 0xc8, 0xfc, 0xf5, 0x00, 0x00, 0x00, 0x00, 0xb0, 0x02,
            0xff, 0xff, 0xf3, 0xff, 0x00, 0x00, 0x02, 0x04, 0x05, 0xb4, 0x01, 0x03, 0x03, 0x06, 0x01, 0x01,
            0x08, 0x0a, 0x16, 0xac, 0x9a, 0x99, 0x00, 0x00, 0x00, 0x00, 0x04, 0x02, 0x00, 0x00
        );

        EthernetPacket ether = new EthernetPacket();
        String err = ether.from(bytes);
        assertNull(err);

        check(ether, EthernetPacket::new);

        TcpPacket tcp = (TcpPacket) ((Ipv4Packet) ether.getPacket()).getPacket();

        assertEquals(62824, tcp.getSrcPort());
        assertEquals(443, tcp.getDstPort());
        assertEquals(3939040501L, tcp.getSeqNum());
        assertEquals(0, tcp.getAckNum());
        assertEquals(44, tcp.getDataOffset());
        assertEquals(2, tcp.getFlags());
        assertTrue(tcp.isSyn());
        assertEquals(65535, tcp.getWindow());
        assertEquals(0xf3ff, tcp.getChecksum());
        assertEquals(8, tcp.getOptions().size());
        assertEquals(0, tcp.getData().length());
    }

    @Test
    public void tcpIpv4PshExample() {
        ByteArray header = ByteArray.from(
            0xcc, 0x70, 0xed, 0xc4, 0xe4, 0xf9, 0xf8, 0xff, 0xc2, 0x07, 0x89, 0x6e, 0x08, 0x00, 0x45, 0x00,
            0x02, 0x2d, 0x00, 0x00, 0x40, 0x00, 0x40, 0x06, 0x85, 0xf7, 0x0a, 0xf2, 0xc2, 0x70, 0xb4, 0x65,
            0x31, 0x0c, 0xf5, 0x68, 0x01, 0xbb, 0xea, 0xc8, 0xfc, 0xf6, 0x24, 0x77, 0x5b, 0x28, 0x50, 0x18,
            0x10, 0x00, 0x0a, 0xa9, 0x00, 0x00
        );
        ByteArray dataPart = ByteArray.from(
            0x16, 0x03, 0x01, 0x02, 0x00, 0x01, 0x00, 0x01, 0xfc, 0x03, 0x03, 0xb7, 0xc2, 0x9f, 0x2e, 0xda,
            0xed, 0x0a, 0xf4, 0x4d, 0x94, 0xdd, 0xdd, 0x32, 0xdb, 0x24, 0x73, 0x0e, 0xd8, 0xae, 0x6e, 0xd4,
            0x17, 0x0a, 0x64, 0x2d, 0x1e, 0xa9, 0xcd, 0x78, 0xa6, 0x03, 0x0f, 0x20, 0xf6, 0x7c, 0xb2, 0x99,
            0xc8, 0xfc, 0x3a, 0x70, 0xbd, 0xbb, 0x40, 0xa4, 0xcb, 0x69, 0xfb, 0x3a, 0xe1, 0xdb, 0xca, 0x58,
            0xe6, 0xe9, 0x67, 0xd4, 0x34, 0xd3, 0x8d, 0xb2, 0x0a, 0xba, 0xcc, 0x44, 0x00, 0x34, 0x13, 0x01,
            0x13, 0x02, 0x13, 0x03, 0xc0, 0x2c, 0xc0, 0x2b, 0xc0, 0x24, 0xc0, 0x23, 0xc0, 0x0a, 0xc0, 0x09,
            0xcc, 0xa9, 0xc0, 0x30, 0xc0, 0x2f, 0xc0, 0x28, 0xc0, 0x27, 0xc0, 0x14, 0xc0, 0x13, 0xcc, 0xa8,
            0x00, 0x9d, 0x00, 0x9c, 0x00, 0x3d, 0x00, 0x3c, 0x00, 0x35, 0x00, 0x2f, 0xc0, 0x08, 0xc0, 0x12,
            0x00, 0x0a, 0x01, 0x00, 0x01, 0x7f, 0xff, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x12, 0x00,
            0x10, 0x00, 0x00, 0x0d, 0x77, 0x77, 0x77, 0x2e, 0x62, 0x61, 0x69, 0x64, 0x75, 0x2e, 0x63, 0x6f,
            0x6d, 0x00, 0x17, 0x00, 0x00, 0x00, 0x0d, 0x00, 0x18, 0x00, 0x16, 0x04, 0x03, 0x08, 0x04, 0x04,
            0x01, 0x05, 0x03, 0x02, 0x03, 0x08, 0x05, 0x08, 0x05, 0x05, 0x01, 0x08, 0x06, 0x06, 0x01, 0x02,
            0x01, 0x00, 0x05, 0x00, 0x05, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x12, 0x00, 0x00, 0x00, 0x10,
            0x00, 0x0e, 0x00, 0x0c, 0x02, 0x68, 0x32, 0x08, 0x68, 0x74, 0x74, 0x70, 0x2f, 0x31, 0x2e, 0x31,
            0x00, 0x0b, 0x00, 0x02, 0x01, 0x00, 0x00, 0x33, 0x00, 0x26, 0x00, 0x24, 0x00, 0x1d, 0x00, 0x20,
            0x30, 0x69, 0xad, 0xde, 0x96, 0x97, 0xa7, 0x85, 0x78, 0x69, 0x3c, 0x3c, 0xfb, 0x03, 0xbc, 0x87,
            0x17, 0x74, 0xaa, 0x7e, 0x91, 0x78, 0xca, 0xec, 0x23, 0x3a, 0x17, 0x30, 0x83, 0x34, 0x28, 0x07,
            0x00, 0x2d, 0x00, 0x02, 0x01, 0x01, 0x00, 0x2b, 0x00, 0x09, 0x08, 0x03, 0x04, 0x03, 0x03, 0x03,
            0x02, 0x03, 0x01, 0x00, 0x0a, 0x00, 0x0a, 0x00, 0x08, 0x00, 0x1d, 0x00, 0x17, 0x00, 0x18, 0x00,
            0x19, 0x00, 0x15, 0x00, 0xd0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00
        );
        ByteArray bytes = header.concat(dataPart);

        EthernetPacket ether = new EthernetPacket();
        String err = ether.from(bytes);
        assertNull(err);

        check(ether, EthernetPacket::new);

        TcpPacket tcp = (TcpPacket) ((Ipv4Packet) ether.getPacket()).getPacket();

        assertEquals(62824, tcp.getSrcPort());
        assertEquals(443, tcp.getDstPort());
        assertEquals(3939040502L, tcp.getSeqNum());
        assertEquals(611801896L, tcp.getAckNum());
        assertEquals(20, tcp.getDataOffset());
        assertEquals(0x018, tcp.getFlags());
        assertTrue(tcp.isPsh());
        assertTrue(tcp.isAck());
        assertEquals(4096, tcp.getWindow());
        assertEquals(0x0aa9, tcp.getChecksum());
        assertEquals(0, tcp.getOptions().size());
        assertEquals(517, tcp.getData().length());

        assertEquals(dataPart, tcp.getData());
    }
}
