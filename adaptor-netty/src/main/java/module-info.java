module io.vproxy.adaptor.netty {
    requires transitive io.vproxy.base;
    requires io.netty.transport;
    requires io.netty.buffer;
    requires io.netty.common;

    exports io.vproxy.adaptor.netty.channel;
    exports io.vproxy.adaptor.netty.concurrent;
    exports io.vproxy.adaptor.netty.channel.wrap;
}
