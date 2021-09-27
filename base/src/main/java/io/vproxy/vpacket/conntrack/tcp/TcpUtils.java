package io.vproxy.vpacket.conntrack.tcp;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.IPv6;
import io.vproxy.vpacket.*;

import java.util.Collections;

public class TcpUtils {
    private TcpUtils() {
    }

    public static TcpPacket buildCommonTcpResponse(TcpEntry tcp) {
        var ret = new TcpPacket();
        ret.setSrcPort(tcp.destination.getPort());
        ret.setDstPort(tcp.source.getPort());
        ret.setSeqNum(tcp.sendingQueue.getFetchSeq());
        ret.setAckNum(tcp.receivingQueue.getAckedSeq());
        ret.setWindow(tcp.receivingQueue.getWindow() / tcp.receivingQueue.getWindowScale());

        return ret;
    }

    public static AbstractIpPacket buildIpResponse(TcpEntry tcp, TcpPacket tcpPkt) {
        if (tcp.source.getAddress() instanceof IPv4) {
            var ipv4 = new Ipv4Packet();
            ipv4.setSrc((IPv4) tcp.destination.getAddress());
            ipv4.setDst((IPv4) tcp.source.getAddress());
            var tcpBytes = tcpPkt.buildIPv4TcpPacket(ipv4, AbstractPacket.FLAG_CHECKSUM_UNNECESSARY);

            ipv4.setVersion(4);
            ipv4.setIhl(5);
            ipv4.setTotalLength(20 + tcpBytes.length());
            ipv4.setTtl(64);
            ipv4.setProtocol(Consts.IP_PROTOCOL_TCP);
            ipv4.setOptions(ByteArray.allocate(0));

            ipv4.setPacket(tcpPkt);
            return ipv4;
        } else {
            var ipv6 = new Ipv6Packet();
            ipv6.setSrc((IPv6) tcp.destination.getAddress());
            ipv6.setDst((IPv6) tcp.source.getAddress());
            var tcpBytes = tcpPkt.buildIPv6TcpPacket(ipv6, AbstractPacket.FLAG_CHECKSUM_UNNECESSARY);

            ipv6.setVersion(6);
            ipv6.setNextHeader(Consts.IP_PROTOCOL_TCP);
            ipv6.setPayloadLength(tcpBytes.length());
            ipv6.setHopLimit(64);
            ipv6.setExtHeaders(Collections.emptyList());

            ipv6.setPacket(tcpPkt);
            return ipv6;
        }
    }

    public static TcpPacket buildAckResponse(TcpEntry tcp) {
        TcpPacket respondTcp = buildCommonTcpResponse(tcp);
        respondTcp.setFlags(Consts.TCP_FLAGS_ACK);
        return respondTcp;
    }

    public static TcpPacket buildRstResponse(TcpEntry tcp) {
        TcpPacket respondTcp = buildCommonTcpResponse(tcp);
        respondTcp.setFlags(Consts.TCP_FLAGS_RST);
        return respondTcp;
    }
}
