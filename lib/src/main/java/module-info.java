module io.vproxy.lib {
    requires kotlin.stdlib;
    requires kotlinx.coroutines.core.jvm;
    requires io.vproxy.dep;
    requires io.vproxy.base;

    exports io.vproxy.lib.common;
    exports io.vproxy.lib.docker;
    exports io.vproxy.lib.docker.entity;
    exports io.vproxy.lib.http;
    exports io.vproxy.lib.http.route;
    exports io.vproxy.lib.http1;
    exports io.vproxy.lib.tcp;
}
