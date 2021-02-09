module vproxy.app {
    requires jdk.unsupported;
    requires vproxy.base;
    requires vproxy.core;
    requires vproxy.extended;
    requires vproxy.lib;

    exports vproxyapp.vproxyx;
    exports vproxyapp.app;
    exports vproxyapp.app.cmd;
    exports vproxyapp.app.cmd.handle.param;
    exports vproxyapp.app.cmd.handle.resource;
    exports vproxyapp.app.util;
    exports vproxyapp.app.args;
    exports vproxyapp.controller;
    exports vproxyapp.process;
}
