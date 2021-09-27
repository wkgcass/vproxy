package io.vproxy.vswitch.iface;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.vfd.DatagramFD;
import io.vproxy.vswitch.Switch;
import io.vproxy.vswitch.util.UserInfo;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.vfd.DatagramFD;
import io.vproxy.vswitch.Switch;
import io.vproxy.vswitch.util.UserInfo;

import java.util.Map;

public class IfaceInitParams {
    public final Switch sw;
    public final SelectorEventLoop loop;
    public final DatagramFD sock;
    public final PacketCallback callback;
    public final Map<String, UserInfo> users;

    public IfaceInitParams(Switch sw,
                           SelectorEventLoop loop,
                           DatagramFD sock,
                           PacketCallback callback,
                           Map<String, UserInfo> users) {
        this.sw = sw;
        this.loop = loop;
        this.sock = sock;
        this.callback = callback;
        this.users = users;
    }

    public interface PacketCallback {
        void alertPacketsArrive(Iface iface);

        void alertDeviceDown(Iface iface);
    }
}
