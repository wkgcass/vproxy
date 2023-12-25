package io.vproxy.vswitch.node;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.IPv6;
import io.vproxy.vpacket.*;
import io.vproxy.vswitch.PacketBuffer;

import java.util.Collections;

public class TcpReset extends Node {
    private final NodeEgress l4output = new NodeEgress("l4-output");

    public TcpReset() {
        super("tcp-reset");
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("tcp-reset", "l4-output", "l4-output", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(l4output);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        // maybe there's no tcp entry when sending rst
        // so we can only use the fields in the received packet

        var inputTcpPkt = pkb.tcpPkt;

        TcpPacket respondTcp = new TcpPacket();
        respondTcp.setSrcPort(inputTcpPkt.getDstPort());
        respondTcp.setDstPort(inputTcpPkt.getSrcPort());
        respondTcp.setSeqNum(inputTcpPkt.getAckNum());
        respondTcp.setAckNum(inputTcpPkt.getSeqNum() + (inputTcpPkt.isSyn() ? 1 : 0));
        respondTcp.setFlags(Consts.TCP_FLAGS_RST | Consts.TCP_FLAGS_ACK);
        respondTcp.setWindow(0);

        AbstractIpPacket ipPkt;
        if (pkb.ipPkt.getSrc() instanceof IPv4) {
            var ipv4 = new Ipv4Packet();
            ipv4.setSrc((IPv4) pkb.ipPkt.getDst());
            ipv4.setDst((IPv4) pkb.ipPkt.getSrc());
            var tcpBytes = respondTcp.buildIPv4TcpPacket(ipv4, AbstractPacket.FLAG_CHECKSUM_UNNECESSARY);

            ipv4.setVersion(4);
            ipv4.setIhl(5);
            ipv4.setTotalLength(20 + tcpBytes.length());
            ipv4.setTtl(64);
            ipv4.setProtocol(Consts.IP_PROTOCOL_TCP);
            ipv4.setOptions(ByteArray.allocate(0));

            ipv4.setPacket(respondTcp);
            ipPkt = ipv4;
        } else {
            var ipv6 = new Ipv6Packet();
            ipv6.setSrc((IPv6) pkb.ipPkt.getDst());
            ipv6.setDst((IPv6) pkb.ipPkt.getSrc());
            var tcpBytes = respondTcp.buildIPv6TcpPacket(ipv6, AbstractPacket.FLAG_CHECKSUM_UNNECESSARY);

            ipv6.setVersion(6);
            ipv6.setNextHeader(Consts.IP_PROTOCOL_TCP);
            ipv6.setPayloadLength(tcpBytes.length());
            ipv6.setHopLimit(64);
            ipv6.setExtHeaders(Collections.emptyList());

            ipv6.setPacket(respondTcp);
            ipPkt = ipv6;
        }

        pkb.replacePacket(ipPkt);
        return _returnnext(pkb, l4output);
    }
}
