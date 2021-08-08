module vproxy.core {
    requires jdk.crypto.ec;
    requires jdk.crypto.cryptoki;

    requires vproxy.base;
    requires vproxy.lib;

    exports vproxy.component.app;
    exports vproxy.component.proxy;
    exports vproxy.component.secure;
    exports vproxy.component.ssl;
    exports vproxy.component.svrgroup;
    exports vproxy.dns;
    exports vproxy.fstack;
    exports vproxy.pool;
    exports vproxy.socks;
    exports vproxy.util;
    exports vproxy.vswitch;
    exports vproxy.vswitch.dispatcher;
    exports vproxy.vswitch.dispatcher.impl;
    exports vproxy.vswitch.iface;
    exports vproxy.vswitch.plugin;
    exports vproxy.vswitch.stack;
    exports vproxy.vswitch.stack.fd;
    exports vproxy.vswitch.util;
}
