module vproxy.extended {
    requires kotlin.stdlib;
    requires kotlinx.coroutines.core.jvm;
    requires vproxy.base;
    requires vproxy.core;
    requires vproxy.lib;

    exports vproxyx;
    exports vproxyx.pktfiltergen;
    exports vproxyx.pktfiltergen.flow;
    exports vproxyx.util;
    exports vproxyx.websocks;
    exports vproxyx.websocks.relay;
    exports vproxyx.websocks.ss;
    exports vproxyx.websocks.ssl;
    exports vproxyx.websocks.udpovertcp;
}
