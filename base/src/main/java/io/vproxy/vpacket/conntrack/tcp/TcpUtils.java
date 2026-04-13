package io.vproxy.vpacket.conntrack.tcp;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.IPv6;
import io.vproxy.vpacket.*;

import java.util.Collections;
import java.util.List;

public class TcpUtils {
    private TcpUtils() {
    }

    public static TcpPacket buildCommonTcpResponse(TcpEntry tcp) {
        var ret = new TcpPacket();
        ret.setSrcPort(tcp.local.getPort());
        ret.setDstPort(tcp.remote.getPort());
        ret.setSeqNum(tcp.sendingQueue.getFetchSeq());
        ret.setAckNum(tcp.receivingQueue.getAckedSeq());
        ret.setWindow(tcp.receivingQueue.getWindow() / tcp.receivingQueue.getWindowScale());

        return ret;
    }

    public static AbstractIpPacket buildIpResponse(TcpEntry tcp, TcpPacket tcpPkt) {
        if (tcp.remote.getAddress() instanceof IPv4) {
            var ipv4 = new Ipv4Packet();
            ipv4.setSrc((IPv4) tcp.local.getAddress());
            ipv4.setDst((IPv4) tcp.remote.getAddress());
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
            ipv6.setSrc((IPv6) tcp.local.getAddress());
            ipv6.setDst((IPv6) tcp.remote.getAddress());
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

    public static TcpPacket buildAckResponseWithSack(TcpEntry tcp, List<SAckTuple> sackBlocks) {
        TcpPacket respondTcp = buildAckResponse(tcp);
        // SACK option: kind(1) + length(1) + N*8 bytes of [start, end) pairs
        // Max 4 blocks = 2 + 32 = 34 bytes (fits in 40-byte option space with other options)
        int maxBlocks = Math.min(sackBlocks.size(), 4);
        int dataLen = maxBlocks * 8;
        ByteArray sackData = ByteArray.allocate(dataLen);
        for (int i = 0; i < maxBlocks; i++) {
            SAckTuple block = sackBlocks.get(i);
            sackData.int32(i * 8, (int) block.seqBeginInclusive);
            sackData.int32(i * 8 + 4, (int) block.seqEndExclusive);
        }
        var optSack = new TcpPacket.TcpOption(respondTcp);
        optSack.setKind(Consts.TCP_OPTION_SACK);
        optSack.setData(sackData);
        respondTcp.getOptions().add(optSack);
        return respondTcp;
    }
}
