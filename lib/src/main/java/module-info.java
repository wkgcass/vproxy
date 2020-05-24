module vproxy.lib {
    requires vproxy.base;
    requires vproxy.core;

    exports vserver;
    exports vserver.route;
    exports vserver.util;
    exports vserver.server;
    exports vclient;
    exports vclient.impl;
}
