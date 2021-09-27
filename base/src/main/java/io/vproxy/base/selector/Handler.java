package io.vproxy.base.selector;

import io.vproxy.vfd.FD;

public interface Handler<CHANNEL extends FD> {
    void accept(HandlerContext<CHANNEL> ctx);

    void connected(HandlerContext<CHANNEL> ctx);

    void readable(HandlerContext<CHANNEL> ctx);

    void writable(HandlerContext<CHANNEL> ctx);

    // the SelectionKey is removed, or event loop is closed
    void removed(HandlerContext<CHANNEL> ctx);
}
