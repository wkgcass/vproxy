package io.vproxy.adaptor.netty.channel;

import io.netty.channel.*;

import java.net.SocketAddress;

public abstract class AbstractVProxyUnsafe implements Channel.Unsafe {
    private final AbstractVProxyChannel channel;
    private RecvByteBufAllocator.Handle recvHandle;

    public AbstractVProxyUnsafe(AbstractVProxyChannel channel) {
        this.channel = channel;
    }

    @Override
    public RecvByteBufAllocator.Handle recvBufAllocHandle() {
        if (recvHandle == null) {
            recvHandle = channel.config().getRecvByteBufAllocator().newHandle();
        }
        return recvHandle;
    }

    @Override
    public void register(EventLoop eventLoop, ChannelPromise promise) {
        eventLoop.register(promise);
    }

    @Override
    public void bind(SocketAddress localAddress, ChannelPromise promise) {
        promise.setFailure(new UnsupportedOperationException());
    }

    @Override
    public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        promise.setFailure(new UnsupportedOperationException());
    }

    @Override
    public void disconnect(ChannelPromise promise) {
        close(promise);
    }

    @Override
    public void closeForcibly() {
        close(channel.newPromise());
    }

    @Override
    public void beginRead() { // do nothing
    }

    @Override
    public void flush() { // do nothing
    }

    @Override
    public ChannelPromise voidPromise() {
        return channel.voidPromise();
    }

    @Override
    public ChannelOutboundBuffer outboundBuffer() {
        return null;
    }
}
