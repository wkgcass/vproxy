package vproxy.vswitch.iface;

public abstract class AbstractIface implements Iface {
    private int baseMTU;
    private boolean floodAllowed;

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

    public String paramsToString() {
        return "mtu " + baseMTU + " flood " + (floodAllowed ? "allow" : "deny");
    }
}
