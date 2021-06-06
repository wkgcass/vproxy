module vproxy.lib {
    requires vproxy.base;
    requires vproxy.core;

    exports vproxy.vclient;
    exports vproxy.vclient.impl;
    exports vproxy.vlibbase;
    exports vproxy.vlibbase.impl;
    exports vproxy.vserver;
    exports vproxy.vserver.impl;
    exports vproxy.vserver.route;
    exports vproxy.vserver.util;
}
