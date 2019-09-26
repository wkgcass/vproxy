module vproxy {
    requires jdk.unsupported;
    requires java.scripting;
    requires java.management;

    // export the processor and related types
    // then the user can write and use their own processors
    exports vproxy.processor;
    exports vproxy.util;
    uses vproxy.processor.ProcessorRegistry;

    // export the main class for user to start
    exports vproxy.app;

    // export json components
    exports vjson;
    // export vserver|vclient components
    exports vserver;
    exports vclient;
}
