package io.vproxy.vswitch;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.vfd.IP;
import io.vproxy.vpacket.*;
import io.vproxy.vpacket.conntrack.tcp.TcpEntry;
import io.vproxy.vpacket.conntrack.tcp.TcpNat;
import io.vproxy.vpacket.conntrack.udp.UdpEntry;
import io.vproxy.vpacket.conntrack.udp.UdpNat;
import io.vproxy.vpacket.tuples.PacketFullTuple;
import io.vproxy.vswitch.iface.Iface;
import io.vproxy.vswitch.node.Node;
import io.vproxy.vswitch.node.TraceDebugger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class PacketBuffer extends PacketDataBuffer {
    public static final int FLAG_VXLAN = 0x00000001;
    public static final int FLAG_IP = 0x00000004;

    public static final int INTERNAL_MASK_PROXY_PROTOCOL = 0x00010000;

    public static PacketBuffer fromVXLanBytes(Iface devin, ByteArray buf, int pktOff, int pad) {
        return new PacketBuffer(devin, buf, pktOff, pad);
    }

    public static PacketBuffer fromEtherBytes(Iface devin, int vni, ByteArray buf, int pktOff, int pad) {
        return new PacketBuffer(devin, vni, buf, pktOff, pad);
    }

    public static PacketBuffer fromIpBytes(Iface devin, int vni, ByteArray buf, int pktOff, int pad) {
        return new PacketBuffer(devin, vni, buf, pktOff, pad, FLAG_IP);
    }

    public static PacketBuffer fromPacket(VXLanPacket pkt) {
        return new PacketBuffer(pkt);
    }

    public static PacketBuffer fromPacket(VirtualNetwork network, EthernetPacket pkt) {
        return new PacketBuffer(network, pkt);
    }

    public static PacketBuffer fromPacket(VirtualNetwork network, AbstractIpPacket pkt) {
        return new PacketBuffer(network, pkt);
    }

    // ----- context -----
    public Node next;
    public final TraceDebugger debugger = new TraceDebugger();
    public boolean ifaceInput; // the packet is input into switch from iface
    public Iface devin; // not null if it's an input packet
    // this will be set when tx dev is determined
    // this will be set before passing to packet filters, and cleared after it's handled
    public Iface devout;
    public int vni; // vni or vlan number, must always be valid
    public VirtualNetwork network; // might be null
    public int flags;

    // ----- packet -----
    public VXLanPacket vxlan;
    public EthernetPacket pkt; // not null if it's an input packet
    public AbstractIpPacket ipPkt;
    public TcpPacket tcpPkt;
    public UdpPacket udpPkt;
    private PacketFullTuple fullTuple;

    // ----- helper fields -----
    // l3
    public Collection<IP> matchedIps;
    // l4
    public TcpEntry tcp = null;
    public UdpEntry udp = null;
    public TcpNat tcpNat = null;
    public UdpNat udpNat = null;

    // 1. set to true in L4, set to false before handling egress filters
    // 2. set to true in ingress filters, set to false before handling
    public boolean fastpath = false;

    public Object fastpathUserData = null; // set in ingress filters, used in L4 when creating entry

    // ----- used by packet filters -----
    // redirect
    public Iface devredirect; // the dev to redirect to
    public boolean reinput; // set to true to input the packet again

    // ignore iface disabled state
    public boolean assumeIfaceEnabled = false;

    // ----- extra -----
    public int internalMask;
    public int mask;
    private Map<Object, Object> userdata;

    // fromVXLanBytes
    private PacketBuffer(Iface devin, ByteArray fullbuf, int pktOff, int pad) {
        super(fullbuf, pktOff, pad);
        this.devin = devin;
        this.flags = FLAG_VXLAN;
    }

    // fromEtherBytes
    private PacketBuffer(Iface devin, int vni, ByteArray fullbuf, int pktOff, int pad) {
        super(fullbuf, pktOff, pad);
        this.devin = devin;
        this.vni = vni;
        this.flags = 0;
    }

    // fromIpBytes
    private PacketBuffer(Iface devin, int vni, ByteArray fullbuf, int pktOff, int pad, int flags) {
        super(fullbuf, pktOff, pad);
        this.devin = devin;
        this.vni = vni;
        this.flags = flags;
    }

    // fromPacket(VXLanPacket)
    private PacketBuffer(VXLanPacket pkt) {
        super(null);
        this.devin = null;
        this.vni = pkt.getVni();
        this.flags = FLAG_VXLAN;
        this.vxlan = pkt;
        initPackets(true, false, false);
    }

    // fromPacket(VirtualNetwork, EthernetPacket)
    private PacketBuffer(VirtualNetwork network, EthernetPacket pkt) {
        super(null);
        this.devin = null;
        this.vni = network.vni;
        this.network = network;
        this.flags = 0;
        this.pkt = pkt;
        initPackets(false, true, false);
    }

    // fromPacket(VirtualNetwork, AbstractIpPacket)
    private PacketBuffer(VirtualNetwork network, AbstractIpPacket pkt) {
        super(null);
        this.devin = null;
        this.vni = network.vni;
        this.network = network;
        this.flags = FLAG_IP;
        this.ipPkt = pkt;
        initPackets(false, false, true);
    }

    @SuppressWarnings("ConstantConditions")
    public String init() {
        AbstractPacket pkt;
        String err;
        if ((flags & FLAG_VXLAN) == FLAG_VXLAN) {
            var vxlan = new VXLanPacket();
            err = vxlan.from(this, true);
            pkt = vxlan;
        } else if ((flags & FLAG_IP) == FLAG_IP) {
            byte b0 = pktBuf.get(0);
            int version = (b0 >> 4) & 0xff;
            if (version == 4) {
                pkt = new Ipv4Packet();
            } else if (version == 6) {
                pkt = new Ipv6Packet();
            } else {
                return "receiving packet with unknown ip version: " + version;
            }
            err = pkt.from(this);
        } else {
            var ether = new EthernetPacket();
            err = ether.from(this, true);
            pkt = ether;
        }
        if (err != null) {
            return err;
        }
        if ((flags & FLAG_VXLAN) == FLAG_VXLAN) {
            this.vxlan = (VXLanPacket) pkt;
            this.vni = this.vxlan.getVni();
            initPackets(true, false, false);
        } else if ((flags & FLAG_IP) == FLAG_IP) {
            this.ipPkt = (AbstractIpPacket) pkt;
            initPackets(false, false, true);
        } else {
            this.pkt = (EthernetPacket) pkt;
            initPackets(false, true, false);
        }
        return null;
    }

    public void clearHelperFields() {
        matchedIps = null;
    }

    public void clearFilterFields() {
        devredirect = null;
        reinput = false;
    }

    @Override
    public void clearBuffers() {
        if (pkt != null) {
            var pkt = this.pkt;
            // prevent recursive invocation
            this.pkt = null;
            pkt.clearRawPacket();
            this.pkt = pkt;
        }
        super.clearBuffers();
    }

    public void clearAndSetPacket(VirtualNetwork network, EthernetPacket pkt) {
        replacePacket(pkt);
        setNetwork(network);
        devin = null;
    }

    public void clearAndSetPacket(VirtualNetwork network, AbstractIpPacket pkt) {
        replacePacket(pkt);
        setNetwork(network);
        devin = null;
    }

    public void replacePacket(EthernetPacket pkt) {
        clearBuffers();
        clearHelperFields();
        this.flags = 0;
        this.pkt = pkt;
        initPackets(false, true, false);
    }

    public void replacePacket(AbstractIpPacket pkt) {
        clearBuffers();
        clearHelperFields();
        this.flags = FLAG_IP;
        this.ipPkt = pkt;
        initPackets(false, false, true);
    }

    private void initPackets(boolean hasVxlan, boolean hasEther, boolean hasIp) {
        if (hasVxlan) {
            this.pkt = vxlan.getPacket();
            hasEther = true;
        } else {
            this.vxlan = null;
        }
        if (hasEther) {
            if (pkt.getPacket() instanceof AbstractIpPacket) {
                this.ipPkt = (AbstractIpPacket) pkt.getPacket();
                hasIp = true;
            } else {
                this.ipPkt = null;
                hasIp = false;
            }
        } else {
            this.pkt = null;
        }
        if (hasIp) {
            if (ipPkt.getPacket() instanceof TcpPacket) {
                this.tcpPkt = (TcpPacket) ipPkt.getPacket();
            } else {
                this.tcpPkt = null;
            }
            if (ipPkt.getPacket() instanceof UdpPacket) {
                this.udpPkt = (UdpPacket) ipPkt.getPacket();
            } else {
                this.udpPkt = null;
            }
        } else {
            this.ipPkt = null;
        }
        this.fullTuple = null;
    }

    public PacketFullTuple getFullTuple() {
        if (this.fullTuple != null) {
            return this.fullTuple;
        }
        if (devin == null) {
            return null; // no input dev
        }
        int devinIndex = devin.getIndex();
        if (devinIndex == 0) {
            return null; // the dev is not fully initialized yet
        }
        if (tcpPkt == null && udpPkt == null) return null;
        int ipProto = tcpPkt != null ? Consts.IP_PROTOCOL_TCP : Consts.IP_PROTOCOL_UDP;
        int tpSrc = tcpPkt != null ? tcpPkt.getSrcPort() : udpPkt.getSrcPort();
        int tpDst = tcpPkt != null ? tcpPkt.getDstPort() : udpPkt.getDstPort();
        var ret = new PacketFullTuple(
            devin.getIndex(),
            pkt.getSrc(), pkt.getDst(),
            ipPkt.getSrc(), ipPkt.getDst(),
            ipProto, tpSrc, tpDst
        );
        this.fullTuple = ret;
        return ret;
    }

    public void setNetwork(VirtualNetwork network) {
        this.vni = network.vni;
        this.network = network;
        if (vxlan != null) {
            vxlan.setVni(network.vni);
        }
    }

    public void executeWithVni(int vni, Runnable r) {
        if (this.vni == vni) {
            r.run();
            return;
        }

        VirtualNetwork networkBackup = this.network;

        this.vni = vni;
        this.network = null;
        if (this.vxlan != null) {
            this.vxlan.setVni(vni);
        }

        r.run();

        setNetwork(networkBackup);
    }

    public void setMatchedIps(Collection<IP> matchedIps) {
        this.matchedIps = matchedIps;
    }

    /**
     * @return true when parsing failed, false otherwise
     */
    public boolean ensurePartialPacketParsed() {
        var bytes = pkt.getPacketBytes();
        if (bytes == null) {
            return false;
        }

        AbstractIpPacket ip = (AbstractIpPacket) pkt.getPacket(); // cast should succeed
        String err;
        if (ip instanceof Ipv6Packet) {
            err = ((Ipv6Packet) ip).from(bytes, false);
        } else {
            err = ip.from(bytes);
        }

        if (err == null) {
            pkt.clearPacketBytes();
            return false;
        }
        assert Logger.lowLevelDebug("received invalid ip packet: " + err + ", drop it");
        return true;
    }

    public boolean ensurePartialPacketParsed(int level) {
        var bytes = pkt.getPacketBytes();
        if (bytes == null) {
            return false;
        }

        AbstractIpPacket ip = (AbstractIpPacket) pkt.getPacket(); // cast should succeed
        String err = ip.initPartial(level);

        if (err == null) {
            return false;
        }
        assert Logger.lowLevelDebug("received invalid ip packet: " + err + ", drop it");
        return true;
    }

    public PacketBuffer copy() {
        var pkb = new PacketBuffer(network, pkt.copy());
        pkb.debugger.setDebugOn(this.debugger.isDebugOn());
        return pkb;
    }

    public Object getUserData(Object key) {
        if (userdata == null) {
            return null;
        }
        return userdata.get(key);
    }

    public Object putUserData(Object key, Object value) {
        if (userdata == null) {
            userdata = new HashMap<>();
        }
        return userdata.put(key, value);
    }

    public Object removeUserData(Object key) {
        if (userdata == null) {
            return null;
        }
        return userdata.remove(key);
    }

    @Override
    public String toString() {
        AbstractPacket pkt = this.pkt;
        if (pkt == null) {
            pkt = ipPkt;
        }
        if (pkt == null) {
            pkt = tcpPkt;
        }
        if (pkt == null) {
            pkt = udpPkt;
        }
        return "PacketBuffer{" +
            "in=" + devin +
            ", vni=" + vni +
            ", pktBuf=" + (pktBuf == null ? "" : pktBuf.toHexString()) +
            ", pkt=" + (pkt == null ? "" : pkt.description()) +
            "}@" + Utils.toHexString(super.hashCode());
    }
}
