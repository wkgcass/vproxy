package io.vproxy.vswitch.iface;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.vfd.DatagramFD;
import io.vproxy.vswitch.Switch;

public class IfaceInitParams {
    public final int ifaceIndex;
    public final Switch sw;
    public final SelectorEventLoop loop;
    public final DatagramFD sock;
    public final PacketCallback callback;

    public IfaceInitParams(int ifaceIndex,
                           Switch sw,
                           SelectorEventLoop loop,
                           DatagramFD sock,
                           PacketCallback callback) {
        this.ifaceIndex = ifaceIndex;
        this.sw = sw;
        this.loop = loop;
        this.sock = sock;
        this.callback = callback;
    }

    public interface PacketCallback {
        void alertPacketsArrive(Iface iface);

        void alertDeviceDown(Iface iface);
    }
}
