module vproxy.extended {
    requires kotlin.stdlib;
    requires kotlinx.coroutines.core.jvm;
    requires vproxy.base;
    requires vproxy.core;
    requires vproxy.lib;

    exports io.vproxy.vproxyx;
    exports io.vproxy.vproxyx.pktfiltergen;
    exports io.vproxy.vproxyx.pktfiltergen.flow;
    exports io.vproxy.vproxyx.util;
    exports io.vproxy.vproxyx.websocks;
    exports io.vproxy.vproxyx.websocks.relay;
    exports io.vproxy.vproxyx.websocks.ss;
    exports io.vproxy.vproxyx.websocks.ssl;
    exports io.vproxy.vproxyx.websocks.uot;
}
