module vproxy.lib {
    requires kotlin.stdlib;
    requires kotlinx.coroutines.core.jvm;
    requires vproxy.base;

    exports vproxy.lib.common;
    exports vproxy.lib.http;
    exports vproxy.lib.http.route;
    exports vproxy.lib.http1;
    exports vproxy.lib.socks;
    exports vproxy.lib.tcp;
}
