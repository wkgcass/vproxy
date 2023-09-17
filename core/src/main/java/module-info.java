module io.vproxy.core {
    requires jdk.crypto.ec;
    requires jdk.crypto.cryptoki;

    requires io.vproxy.pni;

    requires transitive io.vproxy.dep;
    requires transitive io.vproxy.base;
    requires transitive io.vproxy.lib;

    exports io.vproxy.component.app;
    exports io.vproxy.component.proxy;
    exports io.vproxy.component.secure;
    exports io.vproxy.component.ssl;
    exports io.vproxy.component.svrgroup;
    exports io.vproxy.dns;
    exports io.vproxy.pool;
    exports io.vproxy.socks;
    exports io.vproxy.util;
    exports io.vproxy.vswitch;
    exports io.vproxy.vswitch.dispatcher;
    exports io.vproxy.vswitch.dispatcher.impl;
    exports io.vproxy.vswitch.iface;
    exports io.vproxy.vswitch.node;
    exports io.vproxy.vswitch.plugin;
    exports io.vproxy.vswitch.stack.conntrack;
    exports io.vproxy.vswitch.stack.fd;
    exports io.vproxy.vswitch.util;
}
