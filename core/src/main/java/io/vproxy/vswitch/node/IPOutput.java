package io.vproxy.vswitch.node;

import io.vproxy.base.util.Consts;
import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vfd.IPv6;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vpacket.EthernetPacket;
import io.vproxy.vpacket.Ipv4Packet;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.SwitchDelegate;

public class IPOutput extends AbstractNeighborResolve {
    private final SwitchDelegate sw;
    private final NodeEgress ipOutputRoute = new NodeEgress("ip-output-route");

    public IPOutput(SwitchDelegate sw) {
        super("ip-output");
        this.sw = sw;
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        super.initGraph(builder);
        builder.addEdge("ip-output", "ip-output-route", "ip-output-route", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        super.initNode();
        fillEdges(ipOutputRoute);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        // assign mac to the packet
        MacAddress srcMac = pkb.network.ips.lookup(pkb.ipPkt.getSrc());
        if (srcMac == null) {
            assert Logger.lowLevelDebug("cannot find synthetic ip for sending the output packet, try to use any existing ip");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("no mac found for the ip"));
            }

            var ipmac = pkb.network.ips.findAnyIPForRouting(pkb.ipPkt.getDst() instanceof IPv6);
            if (ipmac == null) {
                Logger.shouldNotHappen("cannot find synthetic ip for sending the output packet");
                return _returndrop(pkb);
            }

            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("use mac of ip ").append(ipmac.ip).append(" mac ").append(ipmac.mac));
            }
            srcMac = ipmac.mac;
        }
        // the dstMac is not important, will be filled
        MacAddress dstMac = MacAddress.ZERO;

        // form a ethernet packet
        EthernetPacket ether = new EthernetPacket();
        ether.setDst(dstMac);
        ether.setSrc(srcMac);
        ether.setType((pkb.ipPkt instanceof Ipv4Packet) ? Consts.ETHER_TYPE_IPv4 : Consts.ETHER_TYPE_IPv6);
        ether.setPacket(pkb.ipPkt);

        pkb.replacePacket(ether);

        // route out
        return _returnnext(pkb, ipOutputRoute);
    }

    public void output(PacketBuffer pkb) {
        var res = handle(pkb, null);
        if (res != HandleResult.DROP) {
            sw.scheduler.schedule(pkb);
        } else {
            assert Logger.lowLevelDebug("ip-output returns DROP");
        }
    }
}
