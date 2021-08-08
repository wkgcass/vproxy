package vproxy.vswitch.iface;

import vproxy.base.util.objectpool.CursorList;
import vproxy.vswitch.PacketBuffer;
import vproxy.vswitch.plugin.PacketFilter;

import java.util.LinkedList;
import java.util.List;

public abstract class Iface {
    private int baseMTU;
    private boolean floodAllowed;
    protected IfaceInitParams.PacketCallback callback;
    private final CursorList<PacketBuffer> rcvQ = new CursorList<>(1);
    private final List<PacketFilter> ingressFilters = new LinkedList<>();
    private final List<PacketFilter> egressFilters = new LinkedList<>();

    protected Iface() {
    }

    public void init(IfaceInitParams params) throws Exception {
        this.callback = params.callback;
    }

    public abstract void sendPacket(PacketBuffer pkb);

    public void completeTx() { // default do nothing
    }

    public abstract void destroy();

    public abstract int getLocalSideVni(int hint);

    public abstract int getOverhead();

    public final int getBaseMTU() {
        return baseMTU;
    }

    public final void setBaseMTU(int baseMTU) {
        this.baseMTU = baseMTU;
    }

    public final boolean isFloodAllowed() {
        return floodAllowed;
    }

    public final void setFloodAllowed(boolean floodAllowed) {
        this.floodAllowed = floodAllowed;
    }

    public final PacketBuffer pollPacket() {
        if (rcvQ.isEmpty()) return null;
        return rcvQ.remove(rcvQ.size() - 1);
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

    public final List<PacketFilter> getIngressFilters() {
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

    public final List<PacketFilter> getEgressFilters() {
        return egressFilters;
    }

    protected final void received(PacketBuffer pkb) {
        rcvQ.add(pkb);
    }

    public abstract String name();

    public final String paramsToString() {
        return "mtu " + baseMTU + " flood " + (floodAllowed ? "allow" : "deny");
    }
}
