package io.vproxy.adaptor.netty.channel.wrap;

import io.netty.channel.*;

import java.net.SocketAddress;

public abstract class DelegateUnsafe implements Channel.Unsafe {
    private final Channel self;

    public DelegateUnsafe(Channel self) {
        this.self = self;
    }

    abstract protected Channel delegate();

    abstract protected void unsetLoop();

    private Channel ensureDelegate() {
        var ch = delegate();
        if (ch == null) {
            throw new IllegalStateException("delegate channel not created, you must call bind/connect first");
        }
        return ch;
    }

    @SuppressWarnings("deprecation")
    @Override
    public RecvByteBufAllocator.Handle recvBufAllocHandle() {
        return ensureDelegate().unsafe().recvBufAllocHandle();
    }

    @Override
    public SocketAddress localAddress() {
        return ensureDelegate().unsafe().localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return ensureDelegate().unsafe().remoteAddress();
    }

    @Override
    public void register(EventLoop eventLoop, ChannelPromise promise) {
        //noinspection deprecation
        eventLoop.register(ensureDelegate(), promise);
    }

    @Override
    public void disconnect(ChannelPromise promise) {
        if (delegate() != null) {
            delegate().disconnect(promise);
        } else {
            promise.setFailure(new UnsupportedOperationException());
        }
    }

    @Override
    public void close(ChannelPromise promise) {
        if (delegate() != null) {
            var p = delegate().newPromise();
            delegate().unsafe().close(p);
            p.addListener(f -> {
                if (f.cause() != null) {
                    promise.setFailure(f.cause());
                } else {
                    promise.setSuccess();
                }
                // regardless of the error
                unsetLoop();
            });
        } else {
            promise.setSuccess();
            unsetLoop();
        }
    }

    @Override
    public void closeForcibly() {
        if (delegate() != null) {
            delegate().unsafe().closeForcibly();
        }
        unsetLoop();
    }

    @Override
    public void deregister(ChannelPromise promise) {
        if (delegate() != null) {
            var p = delegate().newPromise();
            delegate().unsafe().deregister(p);
            p.addListener(f -> {
                if (f.cause() != null) {
                    promise.setFailure(f.cause());
                } else {
                    promise.setSuccess();
                }
                // regardless of the error
                unsetLoop();
            });
        } else {
            promise.setSuccess();
            unsetLoop();
        }
    }

    @Override
    public void beginRead() {
        if (delegate() != null) {
            delegate().unsafe().beginRead();
        }
    }

    @Override
    public void write(Object msg, ChannelPromise promise) {
        if (delegate() != null) {
            delegate().unsafe().write(msg, promise);
        } else {
            promise.setFailure(new IllegalStateException());
        }
    }

    @Override
    public void flush() {
        if (delegate() != null) {
            delegate().unsafe().flush();
        }
    }

    @Override
    public ChannelPromise voidPromise() {
        return self.voidPromise();
    }

    @Override
    public ChannelOutboundBuffer outboundBuffer() {
        return null;
    }
}
