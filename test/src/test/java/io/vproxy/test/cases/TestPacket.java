package vproxy.test.cases;

import org.junit.Test;
import vproxy.base.util.ByteArray;
import vproxy.base.util.Consts;
import vproxy.base.util.Utils;
import vproxy.vfd.IP;
import vproxy.vfd.IPv4;
import vproxy.vfd.IPv6;
import vproxy.vfd.MacAddress;
import vproxy.vpacket.*;

import java.util.Collections;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Consumer;
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
        arp.setSenderIp(randomIpv4().bytes);
        arp.setTargetMac(randomMac().bytes);
        arp.setTargetIp(randomIpv4().bytes);
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
        p.clearAllRawPackets();
        ByteArray bytes = p.getRawPacket(0);
        T p2 = constructor.get();
        String err = p2.from(new PacketDataBuffer(bytes));
        assertNull(err);
        assertEquals(p, p2);
        System.out.println("expect: " + p);
        System.out.println("actual: " + p2);
        p2.clearAllRawPackets();
        ByteArray bytes2 = p2.getRawPacket(0);
        assertEquals(bytes, bytes2);

        assertEquals(p, p.copy());
        assertEquals(bytes, p.copy().getRawPacket(0));
    }

    <T extends AbstractPacket> void checkPartialAndModify(ByteArray bytes,
                                                          Supplier<T> constructor,
                                                          BiFunction<T, PacketDataBuffer, String> initPartial,
                                                          Consumer<T> func) {
        // ensure the original bytes won't be modified
        bytes = bytes.copy();

        T p = constructor.get();
        ByteArray bytesX = bytes.copy();
        String err = initPartial.apply(p, new PacketDataBuffer(bytesX));
        assertNull(err);

        // modify the partially initiated packet
        func.accept(p);
        // retrieve the bytes
        ByteArray modified = p.getRawPacket(0);
        // must be the same array
        assertSame(bytesX, modified);

        {
            T px = constructor.get();
            px.from(new PacketDataBuffer(bytesX));
            System.out.println("partial: " + px);
        }

        T p2 = constructor.get();
        err = p2.from(new PacketDataBuffer(bytes.copy()));
        assertNull(err);

        // clear raw packet to ensure a full re-gen
        p2.clearAllRawPackets();
        // modify packet field
        func.accept(p2);
        // generate the packet
        ByteArray gen = p2.getRawPacket(0);

        {
            T px = constructor.get();
            px.from(new PacketDataBuffer(gen));
            System.out.println("full   : " + px);
        }

        // the bytes should be the same
        assertEquals(gen, modified);
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
        String err = ipv4.initPartial(new PacketDataBuffer(bytes));
        assertNull(err);
        assertTrue(ipv4.getPacket() instanceof IcmpPacket);
        assertEquals(IP.from("192.168.3.96"), ipv4.getSrc());
        assertEquals(IP.from("192.168.3.1"), ipv4.getDst());

        ipv4 = new Ipv4Packet();
        err = ipv4.from(new PacketDataBuffer(bytes));
        assertNull(err);
        assertTrue(ipv4.getPacket() instanceof IcmpPacket);

        ipv4.clearAllRawPackets();
        ByteArray gen = ipv4.getRawPacket(0);
        assertEquals(bytes, gen);

        check(ipv4, Ipv4Packet::new);

        checkPartialAndModify(bytes, Ipv4Packet::new, Ipv4Packet::initPartial, p -> p.setSrc((IPv4) IP.from("1.2.3.4")));
        checkPartialAndModify(bytes, Ipv4Packet::new, Ipv4Packet::initPartial, p -> p.setDst((IPv4) IP.from("1.2.3.4")));
        checkPartialAndModify(bytes, Ipv4Packet::new, Ipv4Packet::initPartial, p -> p.setTtl(5));
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
        String err = ipv6.initPartial(new PacketDataBuffer(bytes));
        assertNull(err);
        assertEquals(IP.from("::1"), ipv6.getSrc());
        assertEquals(IP.from("::1"), ipv6.getDst());

        ipv6 = new Ipv6Packet();
        err = ipv6.from(new PacketDataBuffer(bytes));
        assertNull(err);
        assertTrue(ipv6.getPacket() instanceof IcmpPacket);

        ipv6.clearAllRawPackets();
        ByteArray gen = ipv6.getRawPacket(0);
        assertEquals(bytes, gen);

        check(ipv6, Ipv6Packet::new);

        checkPartialAndModify(bytes, Ipv6Packet::new, Ipv6Packet::initPartial, p -> p.setSrc((IPv6) IP.from("::2")));
        checkPartialAndModify(bytes, Ipv6Packet::new, Ipv6Packet::initPartial, p -> p.setDst((IPv6) IP.from("::2")));
        checkPartialAndModify(bytes, Ipv6Packet::new, Ipv6Packet::initPartial, p -> p.setHopLimit(5));
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

        checkPartialAndModify(ether.getRawPacket(0), EthernetPacket::new, (p, b) -> p.from(b, true), p -> p.setSrc(new MacAddress("ab:cd:ef:01:23:45")));
        checkPartialAndModify(ether.getRawPacket(0), EthernetPacket::new, (p, b) -> p.from(b, true), p -> p.setDst(new MacAddress("ab:cd:ef:01:23:45")));
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
        String err = ether.from(new PacketDataBuffer(bytes), true);
        assertNull(err);
        Ipv4Packet ipv4 = (Ipv4Packet) ether.getPacket();
        TcpPacket tcp = (TcpPacket) ipv4.getPacket();
        assertEquals(new MacAddress("cc:70:ed:c4:e4:f9"), ether.getDst());
        assertEquals(new MacAddress("f8:ff:c2:07:89:6e"), ether.getSrc());
        assertEquals(IP.from("10.242.194.112"), ipv4.getSrc());
        assertEquals(IP.from("180.101.49.12"), ipv4.getDst());
        assertEquals(62824, tcp.getSrcPort());
        assertEquals(443, tcp.getDstPort());

        ether = new EthernetPacket();
        err = ether.from(new PacketDataBuffer(bytes));
        assertNull(err);

        check(ether, EthernetPacket::new);

        tcp = (TcpPacket) ((Ipv4Packet) ether.getPacket()).getPacket();

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

        checkPartialAndModify(bytes, EthernetPacket::new, (p, b) -> p.from(b, true),
            p -> ((Ipv4Packet) p.getPacket()).setSrc((IPv4) IP.from("1.2.3.4")));
        checkPartialAndModify(bytes, EthernetPacket::new, (p, b) -> p.from(b, true),
            p -> ((Ipv4Packet) p.getPacket()).setDst((IPv4) IP.from("1.2.3.4")));
        checkPartialAndModify(bytes, EthernetPacket::new, (p, b) -> p.from(b, true),
            p -> ((TcpPacket) ((Ipv4Packet) p.getPacket()).getPacket()).setSrcPort(121));
        checkPartialAndModify(bytes, EthernetPacket::new, (p, b) -> p.from(b, true),
            p -> ((TcpPacket) ((Ipv4Packet) p.getPacket()).getPacket()).setDstPort(121));
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
        String err = ether.from(new PacketDataBuffer(bytes));
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

        checkPartialAndModify(bytes, EthernetPacket::new, (p, b) -> p.from(b, true),
            p -> ((Ipv4Packet) p.getPacket()).setSrc((IPv4) IP.from("1.2.3.4")));
        checkPartialAndModify(bytes, EthernetPacket::new, (p, b) -> p.from(b, true),
            p -> ((Ipv4Packet) p.getPacket()).setDst((IPv4) IP.from("1.2.3.4")));
        checkPartialAndModify(bytes, EthernetPacket::new, (p, b) -> p.from(b, true),
            p -> ((TcpPacket) ((Ipv4Packet) p.getPacket()).getPacket()).setSrcPort(121));
        checkPartialAndModify(bytes, EthernetPacket::new, (p, b) -> p.from(b, true),
            p -> ((TcpPacket) ((Ipv4Packet) p.getPacket()).getPacket()).setDstPort(121));
    }

    @Test
    public void udpIpv4Example() {
        ByteArray header = ByteArray.fromHexString("" +
            "f8ffc207896ed66292eecebf08004500" +
            "005937bd400040117f41c0a80101c0a8" +
            "01440035c9140045df0d");
        ByteArray data = ByteArray.fromHexString("" +
            "15bb8000000100010000000003313237" +
            "013001300131077370656369616c0676" +
            "70726f78790263630000010001c00c00" +
            "01000100000bef00047f000001");
        ByteArray bytes = header.concat(data);

        EthernetPacket ether = new EthernetPacket();
        String err = ether.from(new PacketDataBuffer(bytes));
        assertNull(err);

        check(ether, EthernetPacket::new);

        UdpPacket udp = (UdpPacket) ((Ipv4Packet) ether.getPacket()).getPacket();

        assertEquals(53, udp.getSrcPort());
        assertEquals(51476, udp.getDstPort());
        assertEquals(69, udp.getLength());
        assertEquals(0xdf0d, udp.getChecksum());

        assertEquals(data, udp.getData().getRawPacket(0));

        checkPartialAndModify(bytes, EthernetPacket::new, (p, b) -> p.from(b, true),
            p -> ((Ipv4Packet) p.getPacket()).setSrc((IPv4) IP.from("1.2.3.4")));
        checkPartialAndModify(bytes, EthernetPacket::new, (p, b) -> p.from(b, true),
            p -> ((Ipv4Packet) p.getPacket()).setDst((IPv4) IP.from("1.2.3.4")));
        checkPartialAndModify(bytes, EthernetPacket::new, (p, b) -> p.from(b, true),
            p -> ((UdpPacket) ((Ipv4Packet) p.getPacket()).getPacket()).setSrcPort(121));
        checkPartialAndModify(bytes, EthernetPacket::new, (p, b) -> p.from(b, true),
            p -> ((UdpPacket) ((Ipv4Packet) p.getPacket()).getPacket()).setDstPort(121));
    }
}
