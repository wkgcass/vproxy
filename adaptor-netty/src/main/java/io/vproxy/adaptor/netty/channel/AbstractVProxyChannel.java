package io.vproxy.adaptor.netty.channel;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.util.DefaultAttributeMap;
import io.vproxy.base.connection.NetEventLoop;

import java.net.SocketAddress;

public abstract class AbstractVProxyChannel extends DefaultAttributeMap implements Channel {
    private ChannelId id;
    private Unsafe unsafe;
    private ChannelMetadata metadata;
    private ChannelConfig config;
    private DefaultChannelPromise closeFuture;
    private ByteBufAllocator alloc;
    private DefaultChannelPipeline pipeline;
    protected VProxyEventLoop __temporaryEventLoop = null;

    @Override
    public ChannelId id() {
        if (id == null) {
            return id = DefaultChannelId.newInstance();
        }
        return id;
    }

    private EventLoop lastNonNullEventLoop = null;

    @Override
    public EventLoop eventLoop() {
        if (__temporaryEventLoop != null) {
            return __temporaryEventLoop;
        }
        var el = eventLoop0();
        if (el == null) {
            return lastNonNullEventLoop;
        }
        var ret = (EventLoop) el.__nettyEventLoopRef;
        lastNonNullEventLoop = ret;
        return ret;
    }

    abstract protected NetEventLoop eventLoop0();

    @Override
    public ChannelConfig config() {
        if (config == null) {
            return config = config0();
        }
        return config;
    }

    abstract protected ChannelConfig config0();

    @Override
    public boolean isRegistered() {
        return eventLoop() != null;
    }

    @Override
    public ChannelMetadata metadata() {
        if (metadata == null) {
            return metadata = new ChannelMetadata(false);
        }
        return metadata;
    }

    @Override
    public SocketAddress localAddress() {
        return unsafe().localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return unsafe().remoteAddress();
    }

    @Override
    public ChannelFuture closeFuture() {
        return closeFuture0();
    }

    protected DefaultChannelPromise closeFuture0() {
        if (closeFuture == null) {
            return closeFuture = new DefaultChannelPromise(this);
        }
        return closeFuture;
    }

    @Override
    public Unsafe unsafe() {
        if (unsafe == null) {
            return unsafe = unsafe0();
        }
        return unsafe;
    }

    abstract protected Unsafe unsafe0();

    @Override
    public ChannelPipeline pipeline() {
        if (pipeline == null) {
            return pipeline = new DefaultChannelPipeline(this) {
            };
        }
        return pipeline;
    }

    @Override
    public ByteBufAllocator alloc() {
        if (alloc == null) {
            return alloc = alloc0();
        }
        return alloc;
    }

    protected ByteBufAllocator alloc0() {
        return new UnpooledByteBufAllocator(false);
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return pipeline().bind(localAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return pipeline().connect(remoteAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return pipeline().connect(remoteAddress, localAddress);
    }

    @Override
    public ChannelFuture disconnect() {
        return pipeline().disconnect();
    }

    @Override
    public ChannelFuture close() {
        return pipeline().close();
    }

    @Override
    public ChannelFuture deregister() {
        return pipeline().deregister();
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return pipeline().bind(localAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return pipeline().connect(remoteAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return pipeline().connect(remoteAddress, localAddress, promise);
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        return pipeline().disconnect(promise);
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        return pipeline().close(promise);
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        return pipeline().deregister(promise);
    }

    @Override
    public Channel read() {
        // do nothing
        return this;
    }

    @Override
    public ChannelFuture write(Object msg) {
        return pipeline().write(msg);
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        return pipeline().write(msg, promise);
    }

    @Override
    public Channel flush() {
        pipeline().flush();
        return this;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        return pipeline().writeAndFlush(msg, promise);
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return pipeline().writeAndFlush(msg);
    }

    @Override
    public ChannelPromise newPromise() {
        return pipeline().newPromise();
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
        return pipeline().newProgressivePromise();
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        return pipeline().newSucceededFuture();
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return pipeline().newFailedFuture(cause);
    }

    @Override
    public ChannelPromise voidPromise() {
        return pipeline().voidPromise();
    }

    @Override
    public final int compareTo(Channel o) {
        if (this == o) {
            return 0;
        }

        return id().compareTo(o.id());
    }
}
