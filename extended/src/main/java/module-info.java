module vproxy.extended {
    requires vproxy.base;
    requires vproxy.core;
    requires vproxy.lib;

    exports vproxyx;
    exports vproxyx.util;
    exports vproxyx.websocks;
    exports vproxyx.websocks.ssl;
    exports vproxyx.websocks.ss;
    exports vproxyx.websocks.relay;
}
