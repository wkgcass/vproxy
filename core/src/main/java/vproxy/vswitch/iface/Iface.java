package vproxy.vswitch.iface;

import vproxy.base.util.Annotations;
import vproxy.base.util.coll.IntMap;
import vproxy.base.util.coll.RingQueue;
import vproxy.base.util.exception.AlreadyExistException;
import vproxy.vswitch.PacketBuffer;
import vproxy.vswitch.plugin.PacketFilter;

import java.util.ArrayList;

public abstract class Iface {
    private boolean destroyed = false;
    private int baseMTU;
    private boolean floodAllowed;
    private boolean disabled;
    public final IfaceStatistics statistics = new IfaceStatistics();
    protected IfaceInitParams.PacketCallback callback;
    private final RingQueue<PacketBuffer> rcvQ = new RingQueue<>(1);
    private final ArrayList<PacketFilter> ingressFilters = new ArrayList<>();
    private final ArrayList<PacketFilter> egressFilters = new ArrayList<>();
    private Annotations annotations;

    private final IntMap<VLanAdaptorIface> vlanIfaces = new IntMap<>();

    protected Iface() {
    }

    public void init(IfaceInitParams params) throws Exception {
        this.callback = params.callback;
    }

    public abstract void sendPacket(PacketBuffer pkb);

    public void completeTx() { // default do nothing
    }

    public void destroy() {
        destroyed = true;
    }

    public abstract int getLocalSideVni(int hint);

    public abstract int getOverhead();

    public final int getBaseMTU() {
        return baseMTU;
    }

    public final void setBaseMTU(int baseMTU) {
        this.baseMTU = baseMTU;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public final boolean isFloodAllowed() {
        return floodAllowed;
    }

    public final void setFloodAllowed(boolean floodAllowed) {
        this.floodAllowed = floodAllowed;
    }

    public final PacketBuffer pollPacket() {
        return rcvQ.poll();
    }

    public final boolean addIngressFilter(PacketFilter filter) {
        if (ingressFilters.contains(filter)) {
            return false;
        }
        ingressFilters.add(filter);
        return true;
    }

    public final boolean removeIngressFilter(PacketFilter filter) {
        return ingressFilters.remove(filter);
    }

    public final ArrayList<PacketFilter> getIngressFilters() {
        return ingressFilters;
    }

    public final boolean addEgressFilter(PacketFilter filter) {
        if (egressFilters.contains(filter)) {
            return false;
        }
        this.egressFilters.add(filter);
        return true;
    }

    public final boolean removeEgressFilter(PacketFilter filter) {
        return egressFilters.remove(filter);
    }

    public final ArrayList<PacketFilter> getEgressFilters() {
        return egressFilters;
    }

    public final void addVLanAdaptor(VLanAdaptorIface vif) throws AlreadyExistException {
        if (this instanceof VLanAdaptorIface) {
            throw new AlreadyExistException("this interface is a vlan adaptor, cannot add a new vlan adaptor to it");
        }
        if (vlanIfaces.containsKey(vif.remoteVLan)) {
            throw new AlreadyExistException("vlan-adaptor", "vlan." + vif.remoteVLan + ":" + name());
        }
        vlanIfaces.put(vif.remoteVLan, vif);
    }

    public final void removeVLanAdaptor(VLanAdaptorIface vif) {
        var holding = vlanIfaces.remove(vif.remoteVLan);
        if (holding != vif) {
            vlanIfaces.put(holding.remoteVLan, holding);
        }
    }

    public VLanAdaptorIface lookupVLanAdaptor(int vlan) {
        VLanAdaptorIface vif = vlanIfaces.get(vlan);
        if (vif == null) {
            return null;
        }
        if (!vif.isReady()) {
            return null;
        }
        return vif;
    }

    protected final void received(PacketBuffer pkb) {
        rcvQ.add(pkb);
    }

    public abstract String name();

    private String paramsToString() {
        return "mtu " + baseMTU
            + " flood " + (floodAllowed ? "allow" : "deny")
            + " " + (disabled ? "disabled" : "enabled");
    }

    protected String toStringExtra() {
        return "";
    }

    public Annotations getAnnotations() {
        if (annotations == null) {
            annotations = new Annotations();
        }
        return annotations;
    }

    public void setAnnotations(Annotations annotations) {
        this.annotations = annotations;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    @Override
    public final String toString() {
        String extra = toStringExtra();
        return name()
            + (extra == null || extra.isEmpty() ? "" : extra)
            + " " + paramsToString()
            + " " + statistics
            + (annotations == null ? "" : " annotations " + annotations);
    }
}
