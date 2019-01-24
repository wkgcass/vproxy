package net.cassite.vproxy.protocol;

public interface ProtocolHandler {
    void init(ProtocolHandlerContext ctx);

    void readable(ProtocolHandlerContext ctx);

    void exception(ProtocolHandlerContext ctx, Throwable err);

    void end(ProtocolHandlerContext ctx);
}
