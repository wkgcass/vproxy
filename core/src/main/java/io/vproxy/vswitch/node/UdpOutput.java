package io.vproxy.vswitch.node;

import io.vproxy.base.Config;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.AbstractIpPacket;
import io.vproxy.vpacket.UdpPacket;
import io.vproxy.vpacket.conntrack.udp.Datagram;
import io.vproxy.vpacket.conntrack.udp.UdpListenEntry;
import io.vproxy.vpacket.conntrack.udp.UdpUtils;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.SwitchDelegate;
import io.vproxy.vswitch.VirtualNetwork;
import io.vproxy.vswitch.stack.conntrack.EnhancedUDPEntry;

public class UdpOutput extends Node {
    private final SwitchDelegate sw;
    private final NodeEgress l4output = new NodeEgress("l4-output");

    public UdpOutput(SwitchDelegate sw) {
        super("udp-output");
        this.sw = sw;
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("udp-output", "l4-output", "l4-output", DEFAULT_EDGE_DISTANCE);
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
        return _next(pkb, l4output);
    }

    public void sendUdp(VirtualNetwork network, UdpListenEntry udpListen) {
        VProxyThread.current().newUuidDebugInfo();
        assert Logger.lowLevelDebug("sendUdp(" + network + "," + udpListen + ")");

        while (true) {
            Datagram dg = udpListen.sendingQueue.fetch();
            if (dg == null) {
                break;
            }
            UdpPacket udpPkt = UdpUtils.buildCommonUdpResponse(udpListen, dg);
            AbstractIpPacket ipPkt = UdpUtils.buildIpResponse(udpListen, dg, udpPkt);

            var remote = new IPPort(ipPkt.getDst(), udpPkt.getDstPort());
            var local = new IPPort(ipPkt.getSrc(), udpPkt.getSrcPort());

            PacketBuffer pkb = PacketBuffer.fromPacket(network, ipPkt);

            assert Logger.lowLevelDebug("recording udp entry: " + pkb);
            pkb.udp = network.conntrack.recordUdp(remote, local,
                () -> new EnhancedUDPEntry(udpListen, remote, local, network.conntrack, Config.udpTimeout));

            _schedule(sw.scheduler, pkb, l4output);
        }
    }
}
