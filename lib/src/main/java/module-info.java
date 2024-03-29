module io.vproxy.lib {
    requires kotlin.stdlib;
    requires kotlinx.coroutines.core;
    requires vjson;
    requires transitive io.vproxy.dep;
    requires transitive io.vproxy.base;

    exports io.vproxy.lib.common;
    exports io.vproxy.lib.docker;
    exports io.vproxy.lib.docker.entity;
    exports io.vproxy.lib.http;
    exports io.vproxy.lib.http.route;
    exports io.vproxy.lib.http1;
    exports io.vproxy.lib.tcp;
}
