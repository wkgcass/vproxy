package io.vproxy.vswitch.node;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vpacket.*;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.util.SwitchUtils;

public class IcmpPortUnreachableOutput extends Node {
    private final NodeEgress ipOutputRoute = new NodeEgress("ip-output-route");

    public IcmpPortUnreachableOutput() {
        super("icmp-port-unreachable-output");
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("icmp-port-unreachable-output", "ip-output-route", "ip-output-route", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(ipOutputRoute);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        if (pkb.ensurePartialPacketParsed()) {
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("invalid packet"));
            }
            return _returndropSkipErrorDrop();
        }

        var inIpPkt = (AbstractIpPacket) pkb.pkt.getPacket();

        boolean isIpv6 = inIpPkt instanceof Ipv6Packet;
        var srcIp = inIpPkt.getDst();
        var srcMac = pkb.network.ips.lookup(srcIp);
        if (srcMac == null) {
            Logger.shouldNotHappen("cannot find src mac for sending the icmp port unreachable packet");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("no src mac found for routing out the packet"));
            }
            return _returndrop(pkb);
        }
        // build the icmp time exceeded packet content
        var bytesOfTheOriginalIpPacket = inIpPkt.getRawPacket(AbstractPacket.FLAG_CHECKSUM_UNNECESSARY);
        var foo = new PacketBytes();
        foo.setBytes(ByteArray.allocate(0));
        inIpPkt.setPacket(foo);
        int headerLen = inIpPkt.getRawPacket(AbstractPacket.FLAG_CHECKSUM_UNNECESSARY).length();
        var bytesToSetIntoTheIcmpPacket = headerLen + 64;
        var toSet = bytesOfTheOriginalIpPacket;
        if (toSet.length() > bytesToSetIntoTheIcmpPacket) {
            toSet = toSet.sub(0, bytesToSetIntoTheIcmpPacket);
        }

        IcmpPacket icmp = new IcmpPacket(isIpv6);
        icmp.setType(isIpv6 ? Consts.ICMPv6_PROTOCOL_TYPE_DEST_UNREACHABLE : Consts.ICMP_PROTOCOL_TYPE_DEST_UNREACHABLE);
        icmp.setCode(isIpv6 ? Consts.ICMPv6_PROTOCOL_CODE_PORT_UNREACHABLE : Consts.ICMP_PROTOCOL_CODE_PORT_UNREACHABLE);
        icmp.setOther(
            ByteArray.allocate(4) // unused 4 bytes
                .concat(toSet)
        );

        EthernetPacket ether = SwitchUtils.buildEtherIpIcmpPacket(pkb.pkt.getSrc(), srcMac, srcIp, inIpPkt.getSrc(), icmp);
        pkb.replacePacket(ether);

        return _returnnext(pkb, ipOutputRoute);
    }
}
