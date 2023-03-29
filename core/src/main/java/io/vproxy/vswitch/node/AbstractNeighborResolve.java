package io.vproxy.vswitch.node;

import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vpacket.EthernetPacket;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.VirtualNetwork;
import io.vproxy.vswitch.util.SwitchUtils;

public abstract class AbstractNeighborResolve extends Node {
    private final NodeEgress neighborResolveEthernetOutput = new NodeEgress("neighbor-resolve-ethernet-output");

    public AbstractNeighborResolve(String name) {
        super(name);
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge(name, "ethernet-output", "neighbor-resolve-ethernet-output", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(neighborResolveEthernetOutput);
    }

    protected HandleResult resolve(VirtualNetwork network, IP ip, MacAddress knownMac, PacketBuffer pkb) {
        assert Logger.lowLevelDebug("lookupAddress(" + network + "," + ip + "," + knownMac + ")");
        if (ip instanceof IPv4) {
            return arpResolve(network, ip, knownMac, pkb);
        } else {
            return ndpResolve(network, ip, knownMac, pkb);
        }
    }

    private HandleResult arpResolve(VirtualNetwork network, IP ip, MacAddress knownMac, PacketBuffer pkb) {
        assert Logger.lowLevelDebug("lookupAddress(" + network + "," + ip + "," + knownMac + ")");
        var iface = knownMac == null ? null : network.macTable.lookup(knownMac);
        if (iface == null) {
            assert Logger.lowLevelDebug(" cannot find iface of the mac, try broadcast");
            return broadcastArp(network, ip, pkb);
        } else {
            assert Logger.lowLevelDebug(" run unicast");
            return unicastArp(network, ip, knownMac, pkb);
        }
    }

    private HandleResult ndpResolve(VirtualNetwork network, IP ip, MacAddress knownMac, PacketBuffer pkb) {
        assert Logger.lowLevelDebug("lookupAddress(" + network + "," + ip + "," + knownMac + ")");
        var iface = network.macTable.lookup(knownMac);
        if (iface == null) {
            assert Logger.lowLevelDebug("cannot find iface of the mac, try broadcast");
            return broadcastNdp(network, ip, pkb);
        } else {
            assert Logger.lowLevelDebug("run unicast");
            return unicastNdp(network, ip, knownMac, pkb);
        }
    }

    protected HandleResult broadcastArpOrNdp(VirtualNetwork network, IP dst, PacketBuffer pkb) {
        assert Logger.lowLevelDebug("broadcastArpOrNdp(" + network + "," + dst + ")");
        if (dst instanceof IPv4) {
            return broadcastArp(network, dst, pkb);
        } else {
            return broadcastNdp(network, dst, pkb);
        }
    }

    protected HandleResult broadcastArp(VirtualNetwork network, IP dst, PacketBuffer pkb) {
        assert Logger.lowLevelDebug("broadcastArp(" + network + "," + dst + ")");

        EthernetPacket packet = SwitchUtils.buildArpReq(network, dst, SwitchUtils.BROADCAST_MAC);
        if (packet == null) {
            assert Logger.lowLevelDebug("failed to build arp packet");
            return _returndrop(pkb);
        }
        pkb.clearAndSetPacket(network, packet);
        return _returnnext(pkb, neighborResolveEthernetOutput);
    }

    protected HandleResult unicastArp(VirtualNetwork network, IP dst, MacAddress dstMac, PacketBuffer pkb) {
        assert Logger.lowLevelDebug("unicastArp(" + network + "," + dst + "," + dstMac + ")");

        EthernetPacket packet = SwitchUtils.buildArpReq(network, dst, dstMac);
        if (packet == null) {
            assert Logger.lowLevelDebug("failed to build arp packet");
            return _returndrop(pkb);
        }
        pkb.clearAndSetPacket(network, packet);
        return _returnnext(pkb, neighborResolveEthernetOutput);
    }

    protected HandleResult broadcastNdp(VirtualNetwork network, IP dst, PacketBuffer pkb) {
        assert Logger.lowLevelDebug("broadcastNdp(" + network + "," + dst + ")");

        EthernetPacket packet = SwitchUtils.buildNdpNeighborSolicitation(network, dst, SwitchUtils.BROADCAST_MAC);
        if (packet == null) {
            assert Logger.lowLevelDebug("failed to build ndp neighbor solicitation packet");
            return _returndrop(pkb);
        }
        pkb.clearAndSetPacket(network, packet);
        return _returnnext(pkb, neighborResolveEthernetOutput);
    }

    protected HandleResult unicastNdp(VirtualNetwork network, IP dst, MacAddress dstMac, PacketBuffer pkb) {
        assert Logger.lowLevelDebug("unicastNdp(" + network + "," + dst + "," + dstMac + ")");

        EthernetPacket packet = SwitchUtils.buildNdpNeighborSolicitation(network, dst, dstMac);
        if (packet == null) {
            assert Logger.lowLevelDebug("failed to build ndp neighbor solicitation packet");
            return _returndrop(pkb);
        }
        pkb.clearAndSetPacket(network, packet);
        return _returnnext(pkb, neighborResolveEthernetOutput);
    }
}
