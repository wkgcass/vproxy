package io.vproxy.vswitch.node;

import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vpacket.PartialPacket;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.util.CSumRecalcType;

public class DevInput extends Node {
    private final NodeEgress ethernetInput = new NodeEgress("ethernet-input");

    public DevInput() {
        super("dev-input");
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("dev-input", "ethernet-input", "ethernet-input", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(ethernetInput);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        // clear csum if required
        if (pkb.devin.getParams().getCSumRecalc() != CSumRecalcType.none) {
            if (pkb.ipPkt != null) {
                if (pkb.ipPkt.getPacket() != null) {
                    assert Logger.lowLevelDebug("checksum cleared for " + pkb.ipPkt.getPacket().description());
                    pkb.ipPkt.getPacket().clearChecksum();
                }
                if (pkb.devin.getParams().getCSumRecalc() == CSumRecalcType.all) {
                    assert Logger.lowLevelDebug("checksum cleared for " + pkb.ipPkt.description());
                    pkb.ipPkt.clearChecksum();
                }
            }
        }

        // try fastpath, directly send to user apps
        if (pkb.fastpath) {
            pkb.fastpath = false; // clear the field
            if (pkb.udp != null) {
                assert Logger.lowLevelDebug("fastpath for udp on input: " + pkb);
                pkb.udp.update();
                pkb.ensurePartialPacketParsed(PartialPacket.LEVEL_HANDLED_FIELDS);
                pkb.udp.listenEntry.receivingQueue.store(
                    pkb.ipPkt.getSrc(), pkb.udpPkt.getSrcPort(),
                    pkb.udpPkt.getData().getRawPacket(0));

                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger
                        .append("fastpath: udp: remote=")
                        .append(pkb.udp.remote.formatToIPPortString())
                        .append(" local=")
                        .append(pkb.udp.local.formatToIPPortString())
                        .newLine();
                }
                return _return(HandleResult.STOLEN, pkb);
            } else {
                assert Logger.lowLevelDebug("fastpath set but pkb.udp is null");
            }
        }

        assert Logger.lowLevelDebug("no fastpath, handle normally: " + pkb);

        // normal input
        return _returnnext(pkb, ethernetInput);
    }
}
