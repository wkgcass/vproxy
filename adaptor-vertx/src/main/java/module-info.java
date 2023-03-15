module io.vproxy.adaptor.vertx {
    requires io.vertx.core;
    requires io.netty.transport;
    requires io.netty.buffer;
    requires io.netty.common;
    requires io.vproxy.adaptor.netty;

    exports io.vproxy.adaptor.vertx;
}
