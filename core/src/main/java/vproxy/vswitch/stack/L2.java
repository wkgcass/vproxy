package vproxy.vswitch.stack;

import vproxy.base.util.ByteArray;
import vproxy.base.util.Consts;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.vfd.IP;
import vproxy.vfd.MacAddress;
import vproxy.vpacket.AbstractIpPacket;
import vproxy.vpacket.AbstractPacket;
import vproxy.vpacket.ArpPacket;
import vproxy.vpacket.IcmpPacket;
import vproxy.vswitch.SocketBuffer;
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

    public void input(SocketBuffer skb) {
        assert Logger.lowLevelDebug("L2.input(" + skb + ")");

        MacAddress src = skb.pkt.getSrc();

        // record iface in the mac table
        if (skb.devin != null) {
            assert Logger.lowLevelDebug("record the mac -> iface info");
            skb.table.macTable.record(src, skb.devin);
        } else {
            assert Logger.lowLevelDebug("no iface provided with this packet");
        }

        // check whether need to refresh the arp table
        updateArpTable(skb);

        // check whether we should accept the packet and process
        MacAddress dst = skb.pkt.getDst();
        if (dst.isUnicast()) {
            assert Logger.lowLevelDebug("packet is unicast");

            // for unicast, we first search whether we have virtual hosts can accept the packet

            var ips = skb.table.ips.lookupByMac(dst);
            if (ips != null) {
                skb.recordMatchedIps(ips);
                L3.input(skb);
                return;
            }

            assert Logger.lowLevelDebug("no synthetic ip found");
            // then we check whether we can forward this packet out

            Iface output = skb.table.macTable.lookup(dst);
            if (output != null) {
                sendPacket(skb, output);
                return;
            }

            assert Logger.lowLevelDebug("dst not recorded in mac table");
            // the packet will be dropped or flooded

        } else {
            assert Logger.lowLevelDebug("packet is broadcast/multicast");
            // forward the broadcast message
            // and send the packet to local virtual hosts

            sendBroadcast(skb);
            broadcastLocal(skb);
            return;
        }
        handleInputDroppedPacket(skb);
    }

    private void handleInputDroppedPacket(SocketBuffer skb) {
        assert Logger.lowLevelDebug("no path for this packet in l2: " + skb);
        flood(skb);
    }

    private void flood(SocketBuffer skb) {
        Logger.warn(LogType.ALERT, "flood packet: input=" + skb.devin + "," + skb.pkt.description());
        for (Iface iface : swCtx.getIfaces()) {
            if (skb.devin != null && iface == skb.devin) {
                continue;
            }
            if (iface.getLocalSideVni(skb.vni) != skb.vni) {
                continue;
            }
            if (!iface.isFloodAllowed()) {
                continue;
            }
            sendPacket(skb, iface);
        }

        // also, send arp/ndp request for these addresses if they are ip packet
        if (skb.pkt.getPacket() instanceof AbstractIpPacket) {
            AbstractIpPacket ip = (AbstractIpPacket) skb.pkt.getPacket();
            L3.resolve(skb.table, ip.getDst(), null);
        }
    }

    private void updateArpTable(SocketBuffer skb) {
        assert Logger.lowLevelDebug("updateArpTable(" + skb + ")");

        AbstractPacket packet = skb.pkt.getPacket();
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
            if (!skb.table.v4network.contains(ip)) {
                assert Logger.lowLevelDebug("got arp packet not allowed in the network: " + ip + " not in " + skb.table.v4network);
                return;
            }
            skb.table.arpTable.record(skb.pkt.getSrc(), ip);
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
            assert Logger.lowLevelDebug("is ndp");
            var other = icmp.getOther();
            if (other.length() < 28) { // 4 reserved and 16 target address and 8 option
                assert Logger.lowLevelDebug("ndp length not enough");
                return;
            }
            assert Logger.lowLevelDebug("ndp length is ok");
            var targetIp = IP.from(other.sub(4, 16).toJavaArray());
            // check the target ip
            if (skb.table.v6network == null || !skb.table.v6network.contains(targetIp)) {
                assert Logger.lowLevelDebug("got ndp packet not allowed in the network: " + targetIp + " not in " + skb.table.v6network);
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
                skb.table.arpTable.record(mac, ip);
            } else if (optType == Consts.ICMPv6_OPTION_TYPE_Target_Link_Layer_Address) {
                assert Logger.lowLevelDebug("ndp has opt target link layer address");
                // mac is the target's mac, record with target ip in icmp packet
                skb.table.arpTable.record(mac, targetIp);
            }
            // ============================================================
            // ============================================================
            assert Logger.lowLevelDebug("refresh arp table by ndp done");
        }
    }

    public void output(SocketBuffer skb) {
        assert Logger.lowLevelDebug("L2.output(" + skb + ")");
        var packet = skb.pkt;

        var dst = packet.getDst();

        if (dst.isUnicast()) {
            assert Logger.lowLevelDebug("packet is unicast");

            // for unicast, we first search whether we have virtual hosts can accept the packet

            var ips = skb.table.ips.lookupByMac(dst);
            if (ips != null) {
                skb.recordMatchedIps(ips);
                L3.input(skb);
                return;
            }

            assert Logger.lowLevelDebug("no synthetic ip found");
            // then we check whether we can forward this packet out

            Iface iface = skb.table.macTable.lookup(dst);
            if (iface != null) {
                sendPacket(skb, iface);
                return;
            }

            assert Logger.lowLevelDebug("dst not recorded in mac table");
            // the packet will be dropped

        } else {
            assert Logger.lowLevelDebug("packet is broadcast/multicast");
            // forward the broadcast message
            // and send the packet to local virtual hosts except the one that generated this message

            sendBroadcast(skb);
            outputBroadcastLocal(skb);
            return;
        }
        handleOutputDroppedPacket(skb);
    }

    private void handleOutputDroppedPacket(SocketBuffer skb) {
        assert Logger.lowLevelDebug("no path for this packet in l2: " + skb);
        flood(skb);
    }

    private void sendPacket(SocketBuffer skb, Iface iface) {
        assert Logger.lowLevelDebug("sendPacket(" + skb + ", " + iface + ")");
        swCtx.sendPacket(skb, iface);
    }

    private void sendBroadcast(SocketBuffer skb) {
        assert Logger.lowLevelDebug("sendBroadcast(" + skb + ")");

        Set<Iface> sent = new HashSet<>();
        if (skb.devin != null) {
            sent.add(skb.devin);
        }
        for (var entry : skb.table.macTable.listEntries()) {
            if (sent.add(entry.iface)) {
                sendPacket(skb, entry.iface);
            }
        }
        for (Iface f : swCtx.getIfaces()) {
            if (f.getLocalSideVni(skb.vni) == skb.vni) { // send if vni matches or is a remote switch
                if (sent.add(f)) {
                    sendPacket(skb, f);
                }
            }
        }
    }

    private void outputBroadcastLocal(SocketBuffer skb) {
        broadcastLocal(skb);
    }

    private void broadcastLocal(SocketBuffer skb) {
        assert Logger.lowLevelDebug("broadcastLocal(" + skb + ")");

        var allMac = skb.table.ips.allMac();
        boolean handled = false;
        for (MacAddress mac : allMac) {
            if (mac.equals(skb.pkt.getSrc())) { // skip the sender
                continue;
            }
            var ips = skb.table.ips.lookupByMac(mac);
            if (ips == null) {
                Logger.shouldNotHappen("cannot find synthetic ips by mac " + mac + " in vpc " + skb.vni);
                continue;
            }
            assert Logger.lowLevelDebug("broadcast to " + ips);
            skb.recordMatchedIps(ips);
            L3.input(skb);
            handled = true;
        }

        assert handled || Logger.lowLevelDebug("not handled");
    }
}
