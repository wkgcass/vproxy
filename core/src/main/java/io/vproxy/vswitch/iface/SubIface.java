package io.vproxy.vswitch.iface;

public interface SubIface {
    Iface getParentIface();

    boolean isReady();

    void setReady();
}
