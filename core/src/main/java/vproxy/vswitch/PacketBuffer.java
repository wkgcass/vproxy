package vproxy.vswitch;

import vproxy.base.util.ByteArray;
import vproxy.base.util.Logger;
import vproxy.vfd.IP;
import vproxy.vpacket.*;
import vproxy.vpacket.conntrack.tcp.TcpEntry;
import vproxy.vswitch.iface.Iface;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class PacketBuffer extends PacketDataBuffer {
    public static final int FLAG_VXLAN = 0x00000001;
    public static final int FLAG_IP = 0x00000004;

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

    public static PacketBuffer fromPacket(VirtualNetwork network, AbstractEthernetPacket pkt) {
        return new PacketBuffer(network, pkt);
    }

    public static PacketBuffer fromPacket(VirtualNetwork network, AbstractIpPacket pkt) {
        return new PacketBuffer(network, pkt);
    }

    // ----- context -----
    public Iface devin; // not null if it's an input packet
    public Iface devout; // this will only be set before passing to packet filters, and cleared after it's handled
    public int vni; // vni or vlan number, must always be valid
    public VirtualNetwork network; // might be null
    public int flags;

    // ----- packet -----
    public VXLanPacket vxlan;
    public AbstractEthernetPacket pkt; // not null if it's an input packet
    public AbstractIpPacket ipPkt;
    public TcpPacket tcpPkt;

    // ----- helper fields -----
    // l3
    public Collection<IP> matchedIps;
    // l4
    public TcpEntry tcp = null;
    public boolean needTcpReset = false; // this field is only used in L4.input

    // ----- used by packet filters -----
    // redirect
    public Iface devredirect; // the dev to redirect to
    public boolean reinput; // set to true to input the packet again

    // ----- extra -----
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

    // fromPacket(VirtualNetwork, AbstractEthernetPacket)
    private PacketBuffer(VirtualNetwork network, AbstractEthernetPacket pkt) {
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
            this.pkt = (AbstractEthernetPacket) pkt;
            initPackets(false, true, false);
        }
        return null;
    }

    public void clearHelperFields() {
        matchedIps = null;
        tcp = null;
        needTcpReset = false;
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
        } else {
            this.ipPkt = null;
        }
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
    public boolean ensureIPPacketParsed() {
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

    public PacketBuffer copy() {
        return new PacketBuffer(network, pkt.copy());
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
        return "PacketBuffer{" +
            "in=" + devin +
            ", vni=" + vni +
            ", pktBuf=" + (pktBuf == null ? "" : pktBuf.toHexString()) +
            ", pkt=" + (pkt == null ? "" : pkt.description()) +
            '}';
    }
}
