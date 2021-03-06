package vproxy.vswitch.stack;

import vproxy.base.util.ByteArray;
import vproxy.base.util.Consts;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.vfd.IP;
import vproxy.vfd.MacAddress;
import vproxy.vpacket.*;
import vproxy.vswitch.PacketBuffer;
import vproxy.vswitch.SwitchContext;
import vproxy.vswitch.iface.Iface;

import java.util.HashSet;
import java.util.Set;

public class L2 {
    private final SwitchContext swCtx;
    public final L3 L3;

    public L2(SwitchContext swCtx) {
        this.swCtx = swCtx;
        this.L3 = new L3(swCtx, this);
    }

    public void input(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("L2.input(" + pkb + ")");

        MacAddress src = pkb.pkt.getSrc();

        // record iface in the mac table
        if (pkb.devin != null) {
            assert Logger.lowLevelDebug("record the mac -> iface info");
            pkb.table.macTable.record(src, pkb.devin);
        } else {
            assert Logger.lowLevelDebug("no iface provided with this packet");
        }

        // check whether need to refresh the arp table
        updateArpTable(pkb);

        // check whether we should accept the packet and process
        MacAddress dst = pkb.pkt.getDst();
        if (dst.isUnicast()) {
            assert Logger.lowLevelDebug("packet is unicast");

            // for unicast, we first search whether we have virtual hosts can accept the packet

            var ips = pkb.table.ips.lookupByMac(dst);
            if (ips != null) {
                if (pkb.ensureIPPacketParsed()) return;

                pkb.recordMatchedIps(ips);
                L3.input(pkb);
                return;
            }

            assert Logger.lowLevelDebug("no synthetic ip found");
            // then we check whether we can forward this packet out

            Iface output = pkb.table.macTable.lookup(dst);
            if (output != null) {
                sendPacket(pkb, output);
                return;
            }

            assert Logger.lowLevelDebug("dst not recorded in mac table");
            // the packet will be dropped or flooded

        } else {
            assert Logger.lowLevelDebug("packet is broadcast/multicast");
            // forward the broadcast message
            // and send the packet to local virtual hosts

            sendBroadcast(pkb);
            broadcastLocal(pkb);
            return;
        }
        handleInputDroppedPacket(pkb);
    }

    private void handleInputDroppedPacket(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("no path for this packet in l2: " + pkb);
        flood(pkb);
    }

    private void flood(PacketBuffer pkb) {
        if (pkb.pkt.getPacket() instanceof PacketBytes) {
            assert Logger.lowLevelDebug("do not flood packet with unknown ether type, maybe it's randomly generated: "
                + pkb.pkt.description());
            return;
        }

        Logger.warn(LogType.ALERT, "flood packet: " + pkb);
        for (Iface iface : swCtx.getIfaces()) {
            if (pkb.devin != null && iface == pkb.devin) {
                continue;
            }
            if (iface.getLocalSideVni(pkb.vni) != pkb.vni) {
                continue;
            }
            if (!iface.isFloodAllowed()) {
                continue;
            }
            sendPacket(pkb, iface);
        }

        // also, send arp/ndp request for these addresses if they are ip packet
        if (pkb.pkt.getPacket() instanceof AbstractIpPacket) {
            AbstractIpPacket ip = (AbstractIpPacket) pkb.pkt.getPacket();

            if (pkb.ensureIPPacketParsed()) return;

            if (pkb.table.v4network.contains(ip.getDst()) || (pkb.table.v6network != null && pkb.table.v6network.contains(ip.getDst()))) {
                assert Logger.lowLevelDebug("try to resolve " + ip.getDst() + " when flooding");
                L3.resolve(pkb.table, ip.getDst(), null);
            } else {
                assert Logger.lowLevelDebug("cannot resolve " + ip.getDst() + " when flooding because dst is not in current network");
            }
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
            if (!pkb.table.v4network.contains(ip)) {
                assert Logger.lowLevelDebug("got arp packet not allowed in the network: " + ip + " not in " + pkb.table.v4network);
                return;
            }
            pkb.table.arpTable.record(pkb.pkt.getSrc(), ip);
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

            if (pkb.ensureIPPacketParsed()) return;

            var icmp = (IcmpPacket) ipPkt.getPacket();
            if (icmp.getType() != Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation
                &&
                icmp.getType() != Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Advertisement) {
                assert Logger.lowLevelDebug("is not ndp");
                return;
            }
            assert Logger.lowLevelDebug("is ndp");
            var other = icmp.getOther();
            if (other.length() < 28) { // 4 reserved and 16 target address and 8 option
                assert Logger.lowLevelDebug("ndp length not enough");
                return;
            }
            assert Logger.lowLevelDebug("ndp length is ok");
            var targetIp = IP.from(other.sub(4, 16).toJavaArray());
            // check the target ip
            if (pkb.table.v6network == null || !pkb.table.v6network.contains(targetIp)) {
                assert Logger.lowLevelDebug("got ndp packet not allowed in the network: " + targetIp + " not in " + pkb.table.v6network);
                return;
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
                pkb.table.arpTable.record(mac, ip);
            } else if (optType == Consts.ICMPv6_OPTION_TYPE_Target_Link_Layer_Address) {
                assert Logger.lowLevelDebug("ndp has opt target link layer address");
                // mac is the target's mac, record with target ip in icmp packet
                pkb.table.arpTable.record(mac, targetIp);
            }
            // ============================================================
            // ============================================================
            assert Logger.lowLevelDebug("refresh arp table by ndp done");
        }
    }

    public void output(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("L2.output(" + pkb + ")");
        var packet = pkb.pkt;

        var dst = packet.getDst();

        if (dst.isUnicast()) {
            assert Logger.lowLevelDebug("packet is unicast");

            // for unicast, we first check whether we can forward this packet out
            Iface iface = pkb.table.macTable.lookup(dst);
            if (iface != null) {
                sendPacket(pkb, iface);
                return;
            }

            assert Logger.lowLevelDebug("dst not recorded in mac table");

            // then we search whether we have virtual hosts can accept the packet

            var ips = pkb.table.ips.lookupByMac(dst);
            if (ips != null) {
                if (pkb.ensureIPPacketParsed()) return;

                pkb.recordMatchedIps(ips);
                L3.input(pkb);
                return;
            }

            assert Logger.lowLevelDebug("no synthetic ip found");
            // the packet will be dropped

        } else {
            assert Logger.lowLevelDebug("packet is broadcast/multicast");
            // forward the broadcast message
            // and send the packet to local virtual hosts except the one that generated this message

            sendBroadcast(pkb);
            outputBroadcastLocal(pkb);
            return;
        }
        handleOutputDroppedPacket(pkb);
    }

    private void handleOutputDroppedPacket(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("no path for this packet in l2: " + pkb);
        flood(pkb);
    }

    private void sendPacket(PacketBuffer pkb, Iface iface) {
        assert Logger.lowLevelDebug("sendPacket(" + pkb + ", " + iface + ")");
        swCtx.sendPacket(pkb, iface);
    }

    private void sendBroadcast(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("sendBroadcast(" + pkb + ")");

        Set<Iface> sent = new HashSet<>();
        if (pkb.devin != null) {
            sent.add(pkb.devin);
        }
        for (var entry : pkb.table.macTable.listEntries()) {
            if (sent.add(entry.iface)) {
                sendPacket(pkb, entry.iface);
            }
        }
        for (Iface f : swCtx.getIfaces()) {
            if (f.getLocalSideVni(pkb.vni) == pkb.vni) { // send if vni matches or is a remote switch
                if (sent.add(f)) {
                    sendPacket(pkb, f);
                }
            }
        }
    }

    private void outputBroadcastLocal(PacketBuffer pkb) {
        broadcastLocal(pkb);
    }

    private void broadcastLocal(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("broadcastLocal(" + pkb + ")");

        var allMac = pkb.table.ips.allMac();
        boolean handled = false;
        for (MacAddress mac : allMac) {
            if (mac.equals(pkb.pkt.getSrc())) { // skip the sender
                continue;
            }
            var ips = pkb.table.ips.lookupByMac(mac);
            if (ips == null) {
                Logger.shouldNotHappen("cannot find synthetic ips by mac " + mac + " in vpc " + pkb.vni);
                continue;
            }
            assert Logger.lowLevelDebug("broadcast to " + ips);

            if (pkb.ensureIPPacketParsed()) return;

            pkb.recordMatchedIps(ips);
            L3.input(pkb);
            handled = true;
        }

        assert handled || Logger.lowLevelDebug("not handled");
    }
}
