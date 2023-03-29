package io.vproxy.vswitch.node;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vpacket.ArpPacket;
import io.vproxy.vpacket.EthernetPacket;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.util.SwitchUtils;

public abstract class AbstractArpInput extends Node {
    protected final NodeEgress ethernetOutput = new NodeEgress("ethernet-output");

    public AbstractArpInput(String name) {
        super(name);
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge(name, "ethernet-output", "ethernet-output", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(ethernetOutput);
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        ArpPacket arp = (ArpPacket) pkb.pkt.getPacket();
        if (arp.getProtocolType() != Consts.ARP_PROTOCOL_TYPE_IP) {
            assert Logger.lowLevelDebug("type of arp packet is not ip");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.append("type of arp packet is not ip");
            }
            return _returndrop(pkb);
        }
        assert Logger.lowLevelDebug("arp protocol is ip");
        if (arp.getOpcode() != Consts.ARP_PROTOCOL_OPCODE_REQ) {
            assert Logger.lowLevelDebug("cannot handle this type arp message");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.append("cannot handle this type arp message");
            }
            return _returndrop(pkb);
        }
        assert Logger.lowLevelDebug("arp is req");

        // only handle ipv4 in arp, v6 should be handled with ndp
        ByteArray targetIp = arp.getTargetIp();
        if (targetIp.length() != 4) {
            assert Logger.lowLevelDebug("target ip length is not 4");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.append("target ip length is not 4");
            }
            return _returndropSkipErrorDrop();
        }
        assert Logger.lowLevelDebug("arp target is ipv4");
        IP ip = IP.from(targetIp.toJavaArray());

        // check whether we can handle the packet
        if (!pkb.matchedIps.contains(ip)) {
            assert Logger.lowLevelDebug("no matched ip found for the arp packet");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.append("no matched ip found for the arp packet");
            }
            return _returndrop(pkb);
        }

        // handle
        MacAddress mac = pkb.network.ips.lookup(ip);

        assert Logger.lowLevelDebug("respond arp");
        ArpPacket resp = new ArpPacket();
        resp.setHardwareType(arp.getHardwareType());
        resp.setProtocolType(arp.getProtocolType());
        resp.setHardwareSize(arp.getHardwareSize());
        resp.setProtocolSize(arp.getProtocolSize());
        resp.setOpcode(Consts.ARP_PROTOCOL_OPCODE_RESP);
        resp.setSenderMac(mac.bytes);
        resp.setSenderIp(ip.bytes);
        resp.setTargetMac(arp.getSenderMac());
        resp.setTargetIp(arp.getSenderIp());

        EthernetPacket ether = SwitchUtils.buildEtherArpPacket(pkb.pkt.getSrc(), mac, resp);

        pkb.replacePacket(ether);

        return _returnnext(pkb, ethernetOutput);
    }
}
