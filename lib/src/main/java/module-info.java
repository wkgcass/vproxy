module vproxy.lib {
    requires vproxy.base;
    requires vproxy.core;

    exports vserver;
    exports vserver.impl;
    exports vserver.route;
    exports vserver.util;
    exports vlibbase;
    exports vlibbase.impl;
    exports vclient;
    exports vclient.impl;
}
