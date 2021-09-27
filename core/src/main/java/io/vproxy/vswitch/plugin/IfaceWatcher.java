package io.vproxy.vswitch.plugin;

import io.vproxy.vswitch.iface.Iface;

public interface IfaceWatcher {
    void ifaceAdded(Iface iface);

    void ifaceRemoved(Iface iface);
}
