module vproxy.core {
    requires jdk.crypto.ec;
    requires jdk.crypto.cryptoki;

    requires vproxy.base;

    exports vproxy.util;
    exports vproxy.fstack;
    exports vproxy.component.ssl;
    exports vproxy.component.app;
    exports vproxy.component.proxy;
    exports vproxy.component.svrgroup;
    exports vproxy.component.secure;
    exports vproxy.socks;
    exports vproxy.dns;
    exports vproxy.pool;
    exports vswitch;
    exports vswitch.util;
    exports vswitch.iface;
}
