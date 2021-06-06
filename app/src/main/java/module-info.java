module vproxy.app {
    requires jdk.unsupported;
    requires vproxy.base;
    requires vproxy.core;
    requires vproxy.extended;
    requires vproxy.lib;

    exports vproxy.app.app;
    exports vproxy.app.app.args;
    exports vproxy.app.app.cmd;
    exports vproxy.app.app.cmd.handle.param;
    exports vproxy.app.app.cmd.handle.resource;
    exports vproxy.app.app.util;
    exports vproxy.app.controller;
    exports vproxy.app.process;
    exports vproxy.app.vproxyx;
}
