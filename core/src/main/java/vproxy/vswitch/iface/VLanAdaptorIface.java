package vproxy.vswitch.iface;

import vproxy.vpacket.EthernetPacket;
import vproxy.vswitch.PacketBuffer;

public class VLanAdaptorIface extends Iface {
    public final Iface parentIface;
    public final int remoteVLan;
    public final int localVni;
    private boolean ready = false;

    public VLanAdaptorIface(Iface parentIface, int remoteVLan, int localVni) {
        this.parentIface = parentIface;
        this.remoteVLan = remoteVLan;
        this.localVni = localVni;
        overhead = parentIface.getOverhead() + 4 /* for vlan tag */;
    }

    @Override
    public void destroy() {
        super.destroy();
        parentIface.removeVLanAdaptor(this);
    }

    @Override
    public void sendPacket(PacketBuffer pkb) {
        pkb.pkt.setVlan(remoteVLan);
        parentIface.sendPacket(pkb);
    }

    @Override
    public int getLocalSideVni(int hint) {
        return localVni;
    }

    private final int overhead;

    @Override
    public int getOverhead() {
        return overhead;
    }

    @Override
    public String name() {
        return "vlan." + remoteVLan + "@" + parentIface.name();
    }

    @Override
    protected String toStringExtra() {
        return ",vni:" + localVni;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady() {
        this.ready = true;
    }

    public void handle(PacketBuffer pkb) {
        pkb.vni = localVni;
        pkb.pkt.setVlan(EthernetPacket.PENDING_VLAN_CODE);
        pkb.devin = this;
    }
}
