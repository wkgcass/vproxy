package io.vproxy.adaptor.netty.channel;

import io.netty.channel.*;
import io.netty.util.concurrent.*;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.util.anno.Blocking;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.ClosedException;

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class VProxyEventLoopGroup extends AbstractEventLoopGroup implements EventLoopGroup {
    private final Promise<?> terminationFuture = new DefaultPromise<Void>(GlobalEventExecutor.INSTANCE);

    private final io.vproxy.base.component.elgroup.EventLoopGroup elg;

    public VProxyEventLoopGroup() throws IOException {
        this(1);
    }

    public VProxyEventLoopGroup(int n) throws IOException {
        this(new io.vproxy.base.component.elgroup.EventLoopGroup("vproxy-netty-event-loop-group") {{
            for (int i = 0; i < n; ++i) {
                try {
                    add("vproxy-netty-event-loop-" + i);
                } catch (AlreadyExistException | ClosedException e) {
                    throw new RuntimeException(e);
                }
            }
        }});
    }

    public VProxyEventLoopGroup(io.vproxy.base.component.elgroup.EventLoopGroup elg) {
        Objects.requireNonNull(elg);
        this.elg = elg;
    }

    @Override
    public EventLoop next() {
        var el = elg.next();
        return wrapEventLoop(el);
    }

    protected VProxyEventLoop wrapEventLoop(NetEventLoop el) {
        if (el.__nettyEventLoopRef == null) {
            el.__nettyEventLoopRef = new VProxyEventLoop(el);
        }
        return (VProxyEventLoop) el.__nettyEventLoopRef;
    }

    @Override
    public Iterator<EventExecutor> iterator() {
        return elg.list().stream().map(this::wrapEventLoop).map(v -> (EventExecutor) v).iterator();
    }

    @Override
    public ChannelFuture register(Channel channel) {
        return next().register(channel);
    }

    @Override
    public ChannelFuture register(ChannelPromise promise) {
        return next().register(promise);
    }

    @Override
    public ChannelFuture register(Channel channel, ChannelPromise promise) {
        //noinspection deprecation
        return next().register(channel, promise);
    }

    @Override
    public boolean isShuttingDown() {
        return elg.isClosed();
    }

    @Blocking
    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        shutdown();
        return terminationFuture();
    }

    @Override
    public Future<?> terminationFuture() {
        return terminationFuture;
    }

    @Blocking
    @Override
    public void shutdown() {
        elg.close();
        terminationFuture.setSuccess(null);
    }

    @Override
    public boolean isShutdown() {
        return elg.isClosed();
    }

    @Override
    public boolean isTerminated() {
        return elg.isClosed();
    }

    @Blocking
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        shutdown();
        return true;
    }
}
