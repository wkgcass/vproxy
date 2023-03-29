package io.vproxy.vswitch.node;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vpacket.AbstractIpPacket;
import io.vproxy.vpacket.PacketBytes;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.SwitchDelegate;
import io.vproxy.vswitch.iface.Iface;

public class FloodOutput extends AbstractNeighborResolve {
    private final SwitchDelegate sw;
    private final NodeEgress devOutput = new NodeEgress("dev-output");

    public FloodOutput(SwitchDelegate sw) {
        super("flood-output");
        this.sw = sw;
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        super.initGraph(builder);
        builder.addEdge("flood-output", "dev-output", "dev-output", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        super.initNode();
        fillEdges(devOutput);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        if (pkb.pkt.getPacket() instanceof PacketBytes) {
            assert Logger.lowLevelDebug("do not flood packet with unknown ether type, maybe it's randomly generated: "
                + pkb.pkt.description());
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("unknown ether type"));
            }
            return _returndrop(pkb);
        }

        Logger.warn(LogType.ALERT, "flood packet: " + pkb);
        HandleResult res = HandleResult.DROP;
        boolean isFirst = true;
        for (Iface iface : sw.getIfaces()) {
            if (pkb.devin != null && iface == pkb.devin) {
                continue;
            }
            if (iface.getLocalSideVni(pkb.vni) != pkb.vni) {
                continue;
            }
            if (!iface.getParams().isFloodAllowed()) {
                assert Logger.lowLevelDebug("flood not allowed");
                continue;
            }
            if (pkb.ensurePartialPacketParsed()) {
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.line(d -> d.append("invalid packet"));
                }
                return _returndropSkipErrorDrop();
            }
            if (isFirst) {
                isFirst = false;
                res = _next(pkb, devOutput);
            } else {
                var copied = pkb.copy();
                copied.devout = iface;
                _schedule(scheduler, copied, devOutput);
            }
        }

        // also, send arp/ndp request for these addresses if they are ip packet
        if (pkb.pkt.getPacket() instanceof AbstractIpPacket) {
            AbstractIpPacket ip = (AbstractIpPacket) pkb.pkt.getPacket();

            if (pkb.network.v4network.contains(ip.getDst()) || (pkb.network.v6network != null && pkb.network.v6network.contains(ip.getDst()))) {
                assert Logger.lowLevelDebug("try to resolve " + ip.getDst() + " when flooding");
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.line(d -> d.append("try to resolve ip ").append(ip.getDst()));
                }
                var copied = pkb.copy();
                if (resolve(pkb.network, ip.getDst(), null, copied) != HandleResult.DROP) {
                    scheduler.schedule(copied);
                }
            } else {
                assert Logger.lowLevelDebug("cannot resolve " + ip.getDst() + " when flooding because dst is not in current network");
            }
        }

        if (res == HandleResult.DROP && scheduler.isGenerated()) {
            return _returndropSkipErrorDrop();
        }
        return _return(res, pkb);
    }
}
