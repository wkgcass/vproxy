package io.vproxy.vswitch.node;

import io.vproxy.base.util.Consts;
import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.TcpPacket;
import io.vproxy.vpacket.conntrack.tcp.TcpListenEntry;
import io.vproxy.vswitch.PacketBuffer;

public class TcpInput extends Node {
    private final NodeEgress tcpReset = new NodeEgress("tcp-reset");
    private final NodeEgress tcpStack = new NodeEgress("tcp-stack");

    public TcpInput() {
        super("tcp-input");
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("tcp-input", "tcp-reset", "tcp-reset", DEFAULT_EDGE_DISTANCE);
        builder.addEdge("tcp-input", "tcp-stack", "tcp-stack", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(tcpReset);
        fillEdges(tcpStack);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        var ipPkt = pkb.ipPkt;
        var tcpPkt = (TcpPacket) ipPkt.getPacket();
        IPPort src = tcpPkt.getSrc(ipPkt);
        IPPort dst = tcpPkt.getDst(ipPkt);
        var tcpEntry = pkb.network.conntrack.lookupTcp(src, dst);
        if (tcpEntry != null) {
            pkb.tcp = tcpEntry;
        } else if (tcpPkt.getFlags() == Consts.TCP_FLAGS_SYN) {
            // only consider the packets with only SYN on it
            var listenEntry = pkb.network.conntrack.lookupTcpListen(dst);
            if (listenEntry != null) {
                assert Logger.lowLevelDebug("got new connection");

                // check backlog
                if (listenEntry.synBacklog.size() >= TcpListenEntry.MAX_SYN_BACKLOG_SIZE) {
                    assert Logger.lowLevelDebug("syn-backlog is full");
                    // here we reset the connection instead of dropping it like linux
                    if (pkb.debugger.isDebugOn()) {
                        pkb.debugger.line(d -> d.append("syn-backlog is full"));
                    }
                    return _returnnext(pkb, tcpReset);
                } else {
                    tcpEntry = pkb.network.conntrack.createTcp(listenEntry, src, dst, tcpPkt.getSeqNum());
                    listenEntry.synBacklog.add(tcpEntry);
                    pkb.tcp = tcpEntry;
                }
            }
        } else {
            assert Logger.lowLevelDebug("need to reset");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("no tcp entry"));
            }
            return _returnnext(pkb, tcpReset);
        }
        return _returnnext(pkb, tcpStack);
    }
}
