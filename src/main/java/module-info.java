import vfd.FDs;

module vproxy {
    requires jdk.unsupported;
    requires jdk.crypto.ec;
    requires jdk.crypto.cryptoki;

    // export the processor and related types
    // then the user can write and use their own processors
    exports vproxy.processor;
    exports vproxy.util;
    exports vproxy.util.crypto;
    exports vproxy.util.io;
    exports vproxy.util.nio;
    exports vproxy.util.ringbuffer;
    uses vproxy.processor.ProcessorRegistry;
    uses FDs;

    // export json components
    exports vjson;
    // export vserver|vclient components
    exports vserver;
    exports vclient;
}
