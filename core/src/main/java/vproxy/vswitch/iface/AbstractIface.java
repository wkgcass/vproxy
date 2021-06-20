package vproxy.vswitch.iface;

import vproxy.base.util.objectpool.CursorList;
import vproxy.vswitch.PacketBuffer;

public abstract class AbstractIface implements Iface {
    private int baseMTU;
    private boolean floodAllowed;
    protected IfaceInitParams.PacketCallback callback;
    private final CursorList<PacketBuffer> rcvQ = new CursorList<>(1);

    @Override
    public void init(IfaceInitParams params) throws Exception {
        this.callback = params.callback;
    }

    @Override
    public int getBaseMTU() {
        return baseMTU;
    }

    @Override
    public void setBaseMTU(int baseMTU) {
        this.baseMTU = baseMTU;
    }

    @Override
    public boolean isFloodAllowed() {
        return floodAllowed;
    }

    @Override
    public void setFloodAllowed(boolean floodAllowed) {
        this.floodAllowed = floodAllowed;
    }

    @Override
    public PacketBuffer pollPacket() {
        if (rcvQ.isEmpty()) return null;
        return rcvQ.remove(rcvQ.size() - 1);
    }

    protected void received(PacketBuffer pkb) {
        rcvQ.add(pkb);
    }

    public String paramsToString() {
        return "mtu " + baseMTU + " flood " + (floodAllowed ? "allow" : "deny");
    }
}
