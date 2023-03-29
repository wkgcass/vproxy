package io.vproxy.vswitch.node;

import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vfd.IPv6;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vpacket.IcmpPacket;
import io.vproxy.vpacket.Ipv6Packet;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.util.SwitchUtils;

public class IcmpNeighborSolicitationInput extends Node {
    protected final NodeEgress ethernetOutput = new NodeEgress("ethernet-output");

    public IcmpNeighborSolicitationInput() {
        super("icmp-neighbor-solicitation-input");
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("icmp-neighbor-solicitation-input", "ethernet-output", "ethernet-output", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(ethernetOutput);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        if (pkb.ensurePartialPacketParsed()) {
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.append("invalid packet");
            }
            return _returndropSkipErrorDrop();
        }

        var inIpPkt = (Ipv6Packet) pkb.pkt.getPacket();
        var inIcmp = (IcmpPacket) inIpPkt.getPacket();

        IPv6 ndpNeighborSolicitation = SwitchUtils.extractTargetAddressFromNeighborSolicitation(inIcmp);
        if (ndpNeighborSolicitation == null) {
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.append("not valid neighbor solicitation");
            }
            return _returndrop(pkb);
        }

        if (!pkb.matchedIps.contains(ndpNeighborSolicitation)) {
            assert Logger.lowLevelDebug("this ndp ns does not request for matched synthetic ip");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.append("no matched ip found for the neighbor solicitation packet");
            }
            return _returndrop(pkb);
        }

        MacAddress correspondingMac = pkb.network.ips.lookup(ndpNeighborSolicitation);
        if (correspondingMac == null) {
            assert Logger.lowLevelDebug("requested ip is not synthetic ip");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.append("requested ip is not synthetic ip");
            }
            return _returndrop(pkb);
        }

        return respondIcmpNeighborAdvertisement(pkb, ndpNeighborSolicitation, correspondingMac);
    }

    private HandleResult respondIcmpNeighborAdvertisement(PacketBuffer pkb, IPv6 requestedIp, MacAddress requestedMac) {
        assert Logger.lowLevelDebug("respondIcmpNeighborAdvertisement(" + pkb + "," + requestedIp + "," + requestedMac + ")");

        var inIpPkt = (Ipv6Packet) pkb.pkt.getPacket();

        var ipicmp = SwitchUtils.buildNeighborAdvertisementPacket(requestedMac, requestedIp, inIpPkt.getSrc());
        var ether = SwitchUtils.buildEtherIpPacket(pkb.pkt.getSrc(), requestedMac, ipicmp);

        pkb.replacePacket(ether);

        return _next(pkb, ethernetOutput);
    }
}
