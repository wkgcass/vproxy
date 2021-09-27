package io.vproxy.vproxyx.pktfiltergen;

import io.vproxy.vswitch.iface.Iface;

public class IfaceHolder {
    public final String name;
    public Iface iface;

    public IfaceHolder(String name, Iface iface) {
        this.name = name;
        this.iface = iface;
    }
}
