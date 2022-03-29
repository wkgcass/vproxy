package io.vproxy.vpacket.conntrack.udp;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.IPv6;
import io.vproxy.vpacket.*;

import java.util.Collections;

public class UdpUtils {
    private UdpUtils() {
    }

    public static UdpPacket buildCommonUdpResponse(UdpListenEntry udp, Datagram dg) {
        var ret = new UdpPacket();
        ret.setSrcPort(udp.listening.getPort());
        ret.setDstPort(dg.remotePort);
        ret.setLength(8 + dg.data.length());
        ret.setData(new PacketBytes(dg.data));

        return ret;
    }

    public static AbstractIpPacket buildIpResponse(UdpListenEntry udp, Datagram dg, UdpPacket udpPkt) {
        if (udp.listening.getAddress() instanceof IPv4) {
            var ipv4 = new Ipv4Packet();
            ipv4.setSrc((IPv4) udp.listening.getAddress());
            ipv4.setDst((IPv4) dg.remoteIp);
            var udpBytes = udpPkt.buildIPv4UdpPacket(ipv4, AbstractPacket.FLAG_CHECKSUM_UNNECESSARY);

            ipv4.setVersion(4);
            ipv4.setIhl(5);
            ipv4.setTotalLength(20 + udpBytes.length());
            ipv4.setTtl(64);
            ipv4.setProtocol(Consts.IP_PROTOCOL_UDP);
            ipv4.setOptions(ByteArray.allocate(0));

            ipv4.setPacket(udpPkt);
            return ipv4;
        } else {
            var ipv6 = new Ipv6Packet();
            ipv6.setSrc((IPv6) udp.listening.getAddress());
            ipv6.setDst((IPv6) dg.remoteIp);
            var udpBytes = udpPkt.buildIPv6UdpPacket(ipv6, AbstractPacket.FLAG_CHECKSUM_UNNECESSARY);

            ipv6.setVersion(6);
            ipv6.setNextHeader(Consts.IP_PROTOCOL_UDP);
            ipv6.setPayloadLength(udpBytes.length());
            ipv6.setHopLimit(64);
            ipv6.setExtHeaders(Collections.emptyList());

            ipv6.setPacket(udpPkt);
            return ipv6;
        }
    }
}
