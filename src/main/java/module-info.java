module vproxy {
    requires jdk.unsupported;
    requires java.scripting;
    requires java.management;
    // we now definitely need nashorn
    //noinspection removal
    requires jdk.scripting.nashorn;

    // export the processor and related types
    // then the user can write and use their own processors
    exports vproxy.processor;
    exports vproxy.util;
    uses vproxy.processor.ProcessorRegistry;

    // export the main class for user to start
    exports vproxy.app;
}
