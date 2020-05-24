package vproxybase.protocol;

public interface ProtocolHandler<T> {
    void init(ProtocolHandlerContext<T> ctx);

    void readable(ProtocolHandlerContext<T> ctx);

    void exception(ProtocolHandlerContext<T> ctx, Throwable err);

    void end(ProtocolHandlerContext<T> ctx);

    default boolean closeOnRemoval(ProtocolHandlerContext<T> ctx) {
        return true;
    }
}
