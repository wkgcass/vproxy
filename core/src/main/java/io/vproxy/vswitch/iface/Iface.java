package io.vproxy.vswitch.iface;

import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.coll.IntMap;
import io.vproxy.base.util.coll.RingQueue;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.plugin.PacketFilter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public abstract class Iface {
    private boolean destroyed = false;
    private int baseMTU;
    private boolean floodAllowed;
    private boolean disabled;
    public final IfaceStatistics statistics = new IfaceStatistics();
    protected IfaceInitParams.PacketCallback callback;
    private final RingQueue<PacketBuffer> rcvQ = new RingQueue<>(1);
    private final ArrayList<PacketFilter> preHandlers = new ArrayList<>();
    private final ArrayList<PacketFilter> ingressFilters = new ArrayList<>();
    private final ArrayList<PacketFilter> egressFilters = new ArrayList<>();
    private Annotations annotations;

    private final IntMap<VLanAdaptorIface> vlanIfaces = new IntMap<>();

    // ----- extra -----
    private Map<Object, Object> userdata;

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
        // destroy vlans
        for (var vlan : new ArrayList<>(vlanIfaces.values())) {
            vlan.destroy();
        }
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

    public final boolean addPreHandler(PacketFilter filter) {
        if (preHandlers.contains(filter)) {
            return false;
        }
        preHandlers.add(filter);
        return true;
    }

    public final boolean removePreHandler(PacketFilter filter) {
        return preHandlers.remove(filter);
    }

    public final ArrayList<PacketFilter> getPreHandlers() {
        return preHandlers;
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
        if (holding != vif && holding != null) {
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
