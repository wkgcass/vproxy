package io.vproxy.vswitch.iface;

import io.vproxy.vpacket.EthernetPacket;
import io.vproxy.vswitch.PacketBuffer;

public class VLanAdaptorIface extends Iface implements SubIface {
    private final Iface parentIface;
    public final int remoteVLan;
    public final int localVni;
    private boolean ready = false;

    public VLanAdaptorIface(Iface parentIface, int remoteVLan, int localVni) {
        this.parentIface = parentIface;
        this.remoteVLan = remoteVLan;
        this.localVni = localVni;
    }

    @Override
    public Iface getParentIface() {
        return parentIface;
    }

    @Override
    public void destroy() {
        if (isDestroyed()) {
            return;
        }
        super.destroy();
        parentIface.removeVLanAdaptor(this);
        callback.alertDeviceDown(this);
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

    @Override
    public int getOverhead() {
        var parentOverhead = parentIface.getOverhead();
        if (parentOverhead == 0) {
            // 0 means the parent is not encapsulated, so vlan tag won't affect overhead
            return 0;
        }
        return parentOverhead + 4 /* vlan tag */;
    }

    @Override
    public String name() {
        return "vlan." + remoteVLan + "@" + parentIface.name();
    }

    @Override
    protected String toStringExtra() {
        return ",vni:" + localVni;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public void setReady() {
        this.ready = true;
    }

    public void handle(PacketBuffer pkb) {
        pkb.vni = localVni;
        pkb.pkt.setVlan(EthernetPacket.PENDING_VLAN_CODE);
        pkb.devin = this;
    }
}
