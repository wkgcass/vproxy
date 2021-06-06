package vproxy.vswitch.stack;

import vproxy.base.util.ByteArray;
import vproxy.base.util.Consts;
import vproxy.base.util.Logger;
import vproxy.vfd.IP;
import vproxy.vfd.MacAddress;
import vproxy.vpacket.*;
import vproxy.vswitch.Table;
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

    public void input(InputPacketL2Context ctx) {
        assert Logger.lowLevelDebug("L2.input(" + ctx + ")");

        MacAddress src = ctx.inputPacket.getSrc();

        // record iface in the mac table
        if (ctx.inputIface != null) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " record the mac -> iface info");
            ctx.table.macTable.record(src, ctx.inputIface);
        } else {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " no iface provided with this packet");
        }

        // check whether need to refresh the arp table
        updateArpTable(ctx);

        // check whether we should accept the packet and process
        MacAddress dst = ctx.inputPacket.getDst();
        if (dst.isUnicast()) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " packet is unicast");

            // for unicast, we first search whether we have virtual hosts can accept the packet

            var ips = ctx.table.ips.lookupByMac(dst);
            if (ips != null) {
                L3.input(new InputPacketL3Context(ctx, ips, true));
                return;
            }

            assert Logger.lowLevelDebug(ctx.handlingUUID + " no synthetic ip found");
            // then we check whether we can forward this packet out

            Iface output = ctx.table.macTable.lookup(dst);
            if (output != null) {
                forwardPacket(ctx, output);
                return;
            }

            assert Logger.lowLevelDebug(ctx.handlingUUID + " dst not recorded in mac table");
            // the packet will be dropped

        } else {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " packet is broadcast/multicast");
            // forward the broadcast message
            // and send the packet to local virtual hosts

            var vxlanPkt = getOrMakeVXLanPacket(ctx.inputVXLan, ctx.inputPacket, ctx.table);
            sendBroadcast(ctx.handlingUUID, ctx.inputIface, ctx.table, vxlanPkt);
            broadcastLocal(ctx);
            return;
        }
        assert Logger.lowLevelDebug(ctx.handlingUUID + " dropped in l2 " + ctx);
    }

    private void updateArpTable(InputPacketL2Context ctx) {
        assert Logger.lowLevelDebug("updateArpTable(" + ctx + ")");

        AbstractPacket packet = ctx.inputPacket.getPacket();
        if (packet instanceof ArpPacket) {

            assert Logger.lowLevelDebug(ctx.handlingUUID + " is arp packet");
            // ============================================================
            // ============================================================
            ArpPacket arp = (ArpPacket) packet;
            if (arp.getProtocolType() != Consts.ARP_PROTOCOL_TYPE_IP) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + "arp type is not ip");
                return;
            }
            assert Logger.lowLevelDebug(ctx.handlingUUID + " arp protocol is ip");
            if (arp.getOpcode() == Consts.ARP_PROTOCOL_OPCODE_REQ) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " arp is req");
                ByteArray senderIp = arp.getSenderIp();
                if (senderIp.length() != 4) {
                    assert Logger.lowLevelDebug("sender ip length is not 4");
                    return;
                }
                assert Logger.lowLevelDebug(ctx.handlingUUID + " arp sender is ipv4");
                // only handle ipv4 in arp, v6 should be handled with ndp
                IP ip = IP.from(senderIp.toJavaArray());
                if (!ctx.table.v4network.contains(ip)) {
                    assert Logger.lowLevelDebug(ctx.handlingUUID + " got arp packet not allowed in the network: " + ip + " not in " + ctx.table.v4network);
                    return;
                }
                ctx.table.arpTable.record(ctx.inputPacket.getSrc(), ip);
            } else if (arp.getOpcode() == Consts.ARP_PROTOCOL_OPCODE_RESP) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " arp is resp");
                ByteArray senderIp = arp.getSenderIp();
                if (senderIp.length() != 4) {
                    assert Logger.lowLevelDebug("sender ip length is not 4");
                    return;
                }
                // only handle ipv4 for now
                IP ip = IP.from(senderIp.toJavaArray());
                if (!ctx.table.v4network.contains(ip)) {
                    assert Logger.lowLevelDebug(ctx.handlingUUID + "got arp packet not allowed in the network: " + ip + " not in " + ctx.table.v4network);
                    return;
                }
                ctx.table.arpTable.record(ctx.inputPacket.getSrc(), ip);
            } else {
                assert Logger.lowLevelDebug("arp type is neighther req nor resp");
                return;
            }
            // ============================================================
            // ============================================================
            assert Logger.lowLevelDebug(ctx.handlingUUID + " refresh arp table by arp done");

        } else if (packet instanceof AbstractIpPacket) {

            assert Logger.lowLevelDebug(ctx.handlingUUID + " is ip packet");
            // ============================================================
            // ============================================================
            var ipPkt = (AbstractIpPacket) packet;
            if (!(ipPkt.getPacket() instanceof IcmpPacket)) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " is not icmp packet");
                return;
            }
            assert Logger.lowLevelDebug(ctx.handlingUUID + " is icmp packet");
            var icmp = (IcmpPacket) ipPkt.getPacket();
            if (icmp.getType() != Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation
                &&
                icmp.getType() != Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Advertisement) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " is not ndp");
                return;
            }
            assert Logger.lowLevelDebug(ctx.handlingUUID + " is ndp");
            var other = icmp.getOther();
            if (other.length() < 28) { // 4 reserved and 16 target address and 8 option
                assert Logger.lowLevelDebug(ctx.handlingUUID + " ndp length not enough");
                return;
            }
            assert Logger.lowLevelDebug(ctx.handlingUUID + " ndp length is ok");
            var targetIp = IP.from(other.sub(4, 16).toJavaArray());
            // check the target ip
            if (ctx.table.v6network == null || !ctx.table.v6network.contains(targetIp)) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " got ndp packet not allowed in the network: " + targetIp + " not in " + ctx.table.v6network);
                return;
            }

            // try to build arp table
            var optType = other.uint8(20);
            var optLen = other.uint8(21);
            if (optLen != 1) {
                assert Logger.lowLevelDebug("optLen is not 1");
                return;
            }
            assert Logger.lowLevelDebug(ctx.handlingUUID + " ndp optLen == 1");
            var mac = new MacAddress(other.sub(22, 6));
            if (optType == Consts.ICMPv6_OPTION_TYPE_Source_Link_Layer_Address) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " ndp has opt source link layer address");
                // mac is the sender's mac, record with src ip in ip packet
                // this ip address might be solicited node address, but it won't harm to record
                IP ip = ipPkt.getSrc();
                ctx.table.arpTable.record(mac, ip);
            } else if (optType == Consts.ICMPv6_OPTION_TYPE_Target_Link_Layer_Address) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " ndp has opt target link layer address");
                // mac is the target's mac, record with target ip in icmp packet
                ctx.table.arpTable.record(mac, targetIp);
            }
            // ============================================================
            // ============================================================
            assert Logger.lowLevelDebug(ctx.handlingUUID + " refresh arp table by ndp done");
        }
    }

    public void output(OutputPacketL2Context ctx) {
        assert Logger.lowLevelDebug("L2.output(" + ctx + ")");
        var packet = ctx.outputPacket;
        VXLanPacket vxLanPacket = getOrMakeVXLanPacket(null, packet, ctx.table);

        var dst = packet.getDst();

        if (dst.isUnicast()) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " packet is unicast");

            // for unicast, we first search whether we have virtual hosts can accept the packet

            var ips = ctx.table.ips.lookupByMac(dst);
            if (ips != null) {
                var inputCtx = new InputPacketL2Context(ctx.handlingUUID, ctx.table, packet);
                L3.input(new InputPacketL3Context(inputCtx, ips, true));
                return;
            }

            assert Logger.lowLevelDebug(ctx.handlingUUID + " no synthetic ip found");
            // then we check whether we can forward this packet out

            Iface iface = ctx.table.macTable.lookup(dst);
            if (iface != null) {
                sendPacket(ctx.handlingUUID, vxLanPacket, iface);
                return;
            }

            assert Logger.lowLevelDebug(ctx.handlingUUID + " dst not recorded in mac table");
            // the packet will be dropped

        } else {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " packet is broadcast/multicast");
            // forward the broadcast message
            // and send the packet to local virtual hosts except the one that generated this message

            sendBroadcast(ctx.handlingUUID, null, ctx.table, vxLanPacket);
            outputBroadcastLocal(ctx);
            return;
        }
        assert Logger.lowLevelDebug(ctx.handlingUUID + " dropped in l2 " + ctx);
    }

    private void forwardPacket(InputPacketL2Context ctx, Iface output) {
        var p = getOrMakeVXLanPacket(ctx.inputVXLan, ctx.inputPacket, ctx.table);
        sendPacket(ctx.handlingUUID, p, output);
    }

    private VXLanPacket getOrMakeVXLanPacket(VXLanPacket p, AbstractEthernetPacket e, Table t) {
        if (p == null) {
            p = new VXLanPacket();
            p.setVni(t.vni);
            p.setPacket(e);
        }
        return p;
    }

    private void sendPacket(String ctxUUID, VXLanPacket packet, Iface iface) {
        assert Logger.lowLevelDebug(ctxUUID + " sendPacket(" + packet + ", " + iface + ")");
        swCtx.sendPacket(packet, iface);
    }

    private void sendBroadcast(String ctxUUID, Iface inputIface, Table table, VXLanPacket packet) {
        assert Logger.lowLevelDebug(ctxUUID + " sendBroadcast(" + inputIface + ", " + table + ", " + packet + ")");

        Set<Iface> sent = new HashSet<>();
        if (inputIface != null) {
            sent.add(inputIface);
        }
        for (var entry : table.macTable.listEntries()) {
            if (sent.add(entry.iface)) {
                sendPacket(ctxUUID, packet, entry.iface);
            }
        }
        for (Iface f : swCtx.getIfaces()) {
            if (f.getLocalSideVni(table.vni) == table.vni) { // send if vni matches or is a remote switch
                if (sent.add(f)) {
                    sendPacket(ctxUUID, packet, f);
                }
            }
        }
    }

    private void outputBroadcastLocal(OutputPacketL2Context ctx) {
        broadcastLocal(new InputPacketL2Context(ctx.handlingUUID, ctx.table, ctx.outputPacket));
    }

    private void broadcastLocal(InputPacketL2Context ctx) {
        assert Logger.lowLevelDebug("broadcastLocal(" + ctx + ")");

        var allMac = ctx.table.ips.allMac();
        boolean handled = false;
        for (MacAddress mac : allMac) {
            if (mac.equals(ctx.inputPacket.getSrc())) {
                continue;
            }
            var ips = ctx.table.ips.lookupByMac(mac);
            if (ips == null) {
                Logger.shouldNotHappen("cannot find synthetic ips by mac " + mac + " in vpc " + ctx.table.vni);
                continue;
            }
            assert Logger.lowLevelDebug(ctx.handlingUUID + " broadcast to " + ips);
            L3.input(new InputPacketL3Context(ctx, ips, false));
            handled = true;
        }

        assert handled || Logger.lowLevelDebug(ctx.handlingUUID + " not handled");
    }
}
