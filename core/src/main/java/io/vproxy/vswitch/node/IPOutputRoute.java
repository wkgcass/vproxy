package io.vproxy.vswitch.node;

import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vpacket.AbstractIpPacket;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.SwitchDelegate;
import io.vproxy.vswitch.VirtualNetwork;
import io.vproxy.vswitch.util.SwitchUtils;

public class IPOutputRoute extends AbstractNeighborResolve {
    private final SwitchDelegate sw;
    private final NodeEgress ethernetOutput = new NodeEgress("ethernet-output");
    private final NodeEgress ethernetReinput = new NodeEgress("ethernet-reinput");

    public IPOutputRoute(SwitchDelegate sw) {
        super("ip-output-route");
        this.sw = sw;
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        super.initGraph(builder);
        builder.addEdge("ip-output-route", "ethernet-output", "ethernet-output", DEFAULT_EDGE_DISTANCE);
        builder.addEdge("ip-output-route", "ethernet-reinput", "ethernet-reinput", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        super.initNode();
        fillEdges(ethernetOutput);
        fillEdges(ethernetReinput);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        if (!pkb.pkt.getDst().isUnicast()) {
            assert Logger.lowLevelDebug("packet is not unicast, no need to route");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("not unicast"));
            }
            return _returnnext(pkb, ethernetOutput);
        }

        var ipPkt = (AbstractIpPacket) pkb.pkt.getPacket();
        var dst = ipPkt.getDst();
        var routeRule = pkb.network.routeTable.lookup(dst);
        if (routeRule == null) {
            assert Logger.lowLevelDebug("no route rule found for the ip dst, no need to route");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("no route"));
            }
            return determineDstMacAndReturn(pkb);
        }

        assert Logger.lowLevelDebug("route rule found: " + routeRule);

        if (routeRule.toVni == pkb.vni) {
            assert Logger.lowLevelDebug("direct route, no changes required");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("direct route"));
            }
            return determineDstMacAndReturn(pkb);
        } else if (routeRule.toVni != 0) {
            assert Logger.lowLevelDebug("route to another vpc");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("route to another network"));
            }

            // search for any synthetic ip in the target vpc
            VirtualNetwork targetNetwork = sw.getNetwork(routeRule.toVni);
            if (targetNetwork == null) {
                assert Logger.lowLevelDebug("target vpc not found");
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.line(d -> d.append("target network not found"));
                }
                return _returndrop(pkb);
            }
            MacAddress targetMac = SwitchUtils.getRoutedSrcMac(targetNetwork, dst);
            if (targetMac == null) {
                assert Logger.lowLevelDebug("cannot find src/dst mac for sending this packet to another vpc");
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.line(d -> d.append("no src/dst mac found for routing out the packet"));
                }
                return _returndrop(pkb);
            }
            pkb.pkt.setSrc(MacAddress.ZERO); // not important, will be set later
            pkb.pkt.setDst(targetMac);
            pkb.setNetwork(targetNetwork);
            return _returnnext(pkb, ethernetReinput);
        } else {
            assert Logger.lowLevelDebug("route based on ip");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("route via ip"));
            }

            var targetIp = routeRule.ip;
            MacAddress dstMac = pkb.network.lookup(targetIp);
            if (dstMac == null) {
                assert Logger.lowLevelDebug("mac not found in arp table, run a broadcast");
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.line(d -> d.append("gateway mac not found"));
                }
                return broadcastArpOrNdp(pkb.network, targetIp, pkb);
            }

            pkb.pkt.setDst(dstMac);
            return _returnnext(pkb, ethernetOutput);
        }
    }

    private HandleResult determineDstMacAndReturn(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("no route rule or route to current network");
        if (pkb.debugger.isDebugOn()) {
            pkb.debugger.line(d -> d.append("no need to route"));
        }

        // try to find the mac address of the dst
        var dstMac = pkb.network.lookup(pkb.ipPkt.getDst());

        if (dstMac == null) {
            assert Logger.lowLevelDebug("cannot find dst mac for sending the packet");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("cannot find dst mac"));
            }
            return broadcastArpOrNdp(pkb.network, pkb.ipPkt.getDst(), pkb);
        }

        pkb.pkt.setDst(dstMac);
        return _returnnext(pkb, ethernetOutput);
    }
}
