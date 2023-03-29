package io.vproxy.vswitch.node;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vpacket.AbstractIpPacket;
import io.vproxy.vpacket.AbstractPacket;
import io.vproxy.vpacket.ArpPacket;
import io.vproxy.vpacket.IcmpPacket;
import io.vproxy.vswitch.PacketBuffer;

public class EthernetInput extends Node {
    private final NodeEgress unicastInput = new NodeEgress("unicast-input");
    private final NodeEgress multicastInput = new NodeEgress("multicast-input");

    public EthernetInput() {
        super("ethernet-input");
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("ethernet-input", "unicast-input", "unicast-input", DEFAULT_EDGE_DISTANCE);
        builder.addEdge("ethernet-input", "multicast-input", "multicast-input", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(unicastInput);
        fillEdges(multicastInput);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        MacAddress src = pkb.pkt.getSrc();

        // record iface in the mac table
        if (pkb.devin != null) {
            assert Logger.lowLevelDebug("record the mac -> iface info");
            pkb.network.macTable.record(src, pkb.devin);
        } else {
            assert Logger.lowLevelDebug("no iface provided with this packet");
        }

        // check whether need to refresh the arp table
        updateArpTable(pkb);

        // check whether we should accept the packet and process
        MacAddress dst = pkb.pkt.getDst();
        if (dst.isUnicast()) {
            return _returnnext(pkb, unicastInput);
        } else {
            return _returnnext(pkb, multicastInput);
        }
    }

    private void updateArpTable(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("updateArpTable(" + pkb + ")");

        AbstractPacket packet = pkb.pkt.getPacket();
        if (packet instanceof ArpPacket) {

            assert Logger.lowLevelDebug("is arp packet");
            // ============================================================
            // ============================================================
            ArpPacket arp = (ArpPacket) packet;
            if (arp.getProtocolType() != Consts.ARP_PROTOCOL_TYPE_IP) {
                assert Logger.lowLevelDebug("arp type is not ip");
                return;
            }
            assert Logger.lowLevelDebug("arp protocol is ip");
            ByteArray senderIp;
            if (arp.getOpcode() == Consts.ARP_PROTOCOL_OPCODE_REQ) {
                assert Logger.lowLevelDebug("arp is req");
                senderIp = arp.getSenderIp();
                if (senderIp.length() != 4) {
                    assert Logger.lowLevelDebug("sender ip length is not 4");
                    return;
                }
                assert Logger.lowLevelDebug("arp sender is ipv4");
                // fall through: common code
            } else if (arp.getOpcode() == Consts.ARP_PROTOCOL_OPCODE_RESP) {
                assert Logger.lowLevelDebug("arp is resp");
                senderIp = arp.getSenderIp();
                if (senderIp.length() != 4) {
                    assert Logger.lowLevelDebug("sender ip length is not 4");
                    return;
                }
                // fall through: common code
            } else {
                assert Logger.lowLevelDebug("arp type is neither req nor resp");
                return;
            }
            // only handle ipv4 in arp, v6 should be handled with ndp
            IP ip = IP.from(senderIp.toJavaArray());
            if (!pkb.network.v4network.contains(ip)) {
                assert Logger.lowLevelDebug("got arp packet not allowed in the network: " + ip + " not in " + pkb.network.v4network);
                // allow if it's response
                if (arp.getOpcode() != Consts.ARP_PROTOCOL_OPCODE_RESP) {
                    assert Logger.lowLevelDebug("this arp packet is not a response");
                    return;
                }
                if (!pkb.pkt.getDst().isUnicast()) {
                    assert Logger.lowLevelDebug("this arp packet is not unicast");
                    return;
                }
                assert Logger.lowLevelDebug("this arp packet is a response, continue to handle");
            }
            pkb.network.arpTable.record(pkb.pkt.getSrc(), ip);
            // ============================================================
            // ============================================================
            assert Logger.lowLevelDebug("refresh arp table by arp done");

        } else if (packet instanceof AbstractIpPacket) {

            assert Logger.lowLevelDebug("is ip packet");
            // ============================================================
            // ============================================================
            var ipPkt = (AbstractIpPacket) packet;
            if (!(ipPkt.getPacket() instanceof IcmpPacket)) {
                assert Logger.lowLevelDebug("is not icmp packet");
                return;
            }
            assert Logger.lowLevelDebug("is icmp packet");

            var icmp = (IcmpPacket) ipPkt.getPacket();
            if (icmp.getType() != Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation
                &&
                icmp.getType() != Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Advertisement) {
                assert Logger.lowLevelDebug("is not ndp");
                return;
            }

            if (pkb.ensurePartialPacketParsed()) return;

            assert Logger.lowLevelDebug("is ndp");
            var other = icmp.getOther();
            if (other.length() < 28) { // 4 reserved and 16 target address and 8 option
                assert Logger.lowLevelDebug("ndp length not enough");
                return;
            }
            assert Logger.lowLevelDebug("ndp length is ok");
            var targetIp = IP.from(other.sub(4, 16).toJavaArray());
            // check the target ip
            if (pkb.network.v6network == null || !pkb.network.v6network.contains(targetIp)) {
                assert Logger.lowLevelDebug("got ndp packet not allowed in the network: " + targetIp + " not in " + pkb.network.v6network);
                // allow if it's response
                if (icmp.getType() == Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation) {
                    assert Logger.lowLevelDebug("this ndp packet is not neighbor advertisement");
                    return;
                }
                if (!pkb.pkt.getDst().isUnicast()) {
                    assert Logger.lowLevelDebug("this ndp packet is not unicast");
                    return;
                }
                assert Logger.lowLevelDebug("this ndp packet is neighbor advertisement, continue to handle");
            }

            // try to build arp table
            var optType = other.uint8(20);
            var optLen = other.uint8(21);
            if (optLen != 1) {
                assert Logger.lowLevelDebug("optLen is not 1");
                return;
            }
            assert Logger.lowLevelDebug("ndp optLen == 1");
            var mac = new MacAddress(other.sub(22, 6));
            if (optType == Consts.ICMPv6_OPTION_TYPE_Source_Link_Layer_Address) {
                assert Logger.lowLevelDebug("ndp has opt source link layer address");
                // mac is the sender's mac, record with src ip in ip packet
                // this ip address might be solicited node address, but it won't harm to record
                IP ip = ipPkt.getSrc();
                pkb.network.arpTable.record(mac, ip);
            } else if (optType == Consts.ICMPv6_OPTION_TYPE_Target_Link_Layer_Address) {
                assert Logger.lowLevelDebug("ndp has opt target link layer address");
                // mac is the target's mac, record with target ip in icmp packet
                pkb.network.arpTable.record(mac, targetIp);
            }
            // ============================================================
            // ============================================================
            assert Logger.lowLevelDebug("refresh arp table by ndp done");
        }
    }
}
