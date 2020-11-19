module vproxy.lib {
    requires vproxy.base;
    requires vproxy.core;

    exports vserver;
    exports vserver.route;
    exports vserver.util;
    exports vserver.impl;
    exports vclient;
    exports vclient.impl;
    exports vlibbase;
    exports vlibbase.impl;
}
