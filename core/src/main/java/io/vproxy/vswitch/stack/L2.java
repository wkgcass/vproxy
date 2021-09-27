package io.vproxy.vswitch.stack;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vpacket.*;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.SwitchContext;
import io.vproxy.vswitch.iface.Iface;

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
        assert Logger.lowLevelDebug("L2.input(" + pkb + " :: " + pkb.network + ")");

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
            assert Logger.lowLevelDebug("packet is unicast");

            // for unicast, we first check whether we can forward this packet out

            Iface output = pkb.network.macTable.lookup(dst);
            if (output != null) {
                if (pkb.devin == null || pkb.devin != output) {
                    sendPacket(pkb, output);
                } else {
                    assert Logger.lowLevelDebug("drop the packet which would be forwarded out to the same interface as the input interface: " + pkb);
                }
                return;
            }

            assert Logger.lowLevelDebug("dst not recorded in mac table");
            // then we search whether we have virtual hosts can accept the packet

            var ips = pkb.network.ips.lookupByMac(dst);
            if (ips != null) {
                pkb.setMatchedIps(ips);
                L3.input(pkb);
                return;
            }

            assert Logger.lowLevelDebug("no synthetic ip found");
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

    public void reinput(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("reinput");
        pkb.devin = null; // clear devin before re-input
        input(pkb);
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
            if (pkb.ensurePartialPacketParsed()) return;
            var copied = pkb.copy();
            sendPacket(copied, iface);
        }

        // also, send arp/ndp request for these addresses if they are ip packet
        if (pkb.pkt.getPacket() instanceof AbstractIpPacket) {
            AbstractIpPacket ip = (AbstractIpPacket) pkb.pkt.getPacket();

            if (pkb.network.v4network.contains(ip.getDst()) || (pkb.network.v6network != null && pkb.network.v6network.contains(ip.getDst()))) {
                assert Logger.lowLevelDebug("try to resolve " + ip.getDst() + " when flooding");
                L3.resolve(pkb.network, ip.getDst(), null);
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

    public void output(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("L2.output(" + pkb + ")");
        var packet = pkb.pkt;

        var dst = packet.getDst();

        if (dst.isUnicast()) {
            assert Logger.lowLevelDebug("packet is unicast");

            // for unicast, we first check whether we can forward this packet out
            Iface iface = pkb.network.macTable.lookup(dst);
            if (iface != null) {
                sendPacket(pkb, iface);
                return;
            }

            assert Logger.lowLevelDebug("dst not recorded in mac table");

            // then we search whether we have virtual hosts can accept the packet

            var ips = pkb.network.ips.lookupByMac(dst);
            if (ips != null) {
                pkb.setMatchedIps(ips);
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
        assert Logger.lowLevelDebug("sendPacket(" + pkb + ", " + iface.name() + ")");
        swCtx.sendPacket(pkb, iface);
    }

    private void sendBroadcast(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("sendBroadcast(" + pkb + ")");

        Set<Iface> sent = new HashSet<>();
        if (pkb.devin != null) {
            sent.add(pkb.devin);
        }
        for (Iface f : swCtx.getIfaces()) {
            if (f.getLocalSideVni(pkb.vni) == pkb.vni) { // send if vni matches or is a remote switch
                if (sent.add(f)) {
                    if (pkb.ensurePartialPacketParsed()) return;
                    var copied = pkb.copy();
                    sendPacket(copied, f);
                }
            }
        }
    }

    private void outputBroadcastLocal(PacketBuffer pkb) {
        pkb.devin = null; // clear devin info before re-input
        broadcastLocal(pkb);
    }

    private void broadcastLocal(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("broadcastLocal(" + pkb + ")");

        var allMac = pkb.network.ips.allMac();
        boolean handled = false;
        for (MacAddress mac : allMac) {
            if (mac.equals(pkb.pkt.getSrc())) { // skip the sender
                continue;
            }
            var ips = pkb.network.ips.lookupByMac(mac);
            if (ips == null) {
                Logger.shouldNotHappen("cannot find synthetic ips by mac " + mac + " in vpc " + pkb.vni);
                continue;
            }
            assert Logger.lowLevelDebug("broadcast to " + ips);

            if (pkb.ensurePartialPacketParsed()) return;

            var copied = pkb.copy();

            copied.setMatchedIps(ips);
            L3.input(copied);
            handled = true;
        }

        assert handled || Logger.lowLevelDebug("not handled");
    }
}
