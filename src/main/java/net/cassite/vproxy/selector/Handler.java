package net.cassite.vproxy.selector;

import java.nio.channels.SelectableChannel;

public interface Handler<CHANNEL extends SelectableChannel> {
    void accept(HandlerContext<CHANNEL> ctx);

    void connected(HandlerContext<CHANNEL> ctx);

    void readable(HandlerContext<CHANNEL> ctx);

    void writable(HandlerContext<CHANNEL> ctx);
}
