package vproxy.vswitch.plugin;

import vproxy.vswitch.iface.Iface;

public interface IfaceWatcher {
    void ifaceAdded(Iface iface);

    void ifaceRemoved(Iface iface);
}
