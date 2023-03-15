package io.vproxy.adaptor.netty.channel;

import io.netty.channel.*;
import io.netty.util.concurrent.*;
import io.vproxy.adaptor.netty.concurrent.VProxyEventLoopDelayScheduledFuture;
import io.vproxy.base.connection.ConnectableConnection;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.anno.Blocking;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.adaptor.netty.channel.wrap.VProxyInetServerSocketChannel;
import io.vproxy.adaptor.netty.channel.wrap.VProxyInetSocketChannel;
import io.vproxy.vfd.EventSet;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class VProxyEventLoop extends AbstractEventLoop implements EventLoop, EventExecutor {
    private final Promise<?> terminationFuture = new DefaultPromise<Void>(GlobalEventExecutor.INSTANCE);
    private final NetEventLoop el;

    public VProxyEventLoop() throws IOException {
        this(new NetEventLoop(SelectorEventLoop.open()));
    }

    public VProxyEventLoop(NetEventLoop el) {
        Objects.requireNonNull(el);
        this.el = el;
    }

    public NetEventLoop getNetEventLoop() {
        return el;
    }

    @Override
    public ChannelFuture register(Channel channel) {
        return register(channel, channel.newPromise());
    }

    @Override
    public ChannelFuture register(ChannelPromise promise) {
        return register(promise.channel(), promise);
    }

    @Override
    public ChannelFuture register(Channel channel, ChannelPromise promise) {
        if (!(channel instanceof AbstractVProxyChannel)) {
            if (channel.parent() != null && channel.parent().eventLoop() == this) {
                channel.unsafe().register(this, promise);
                return promise;
            }
            promise.setFailure(new IllegalArgumentException("unable to register non-vproxy channels: " + channel));
            return promise;
        }

        beforeRegister(channel);

        try {
            if (channel instanceof VProxyConnectionChannel) {
                registerConnection((VProxyConnectionChannel) channel);
            } else if (channel instanceof VProxyInetSocketChannel) {
                registerWrapSock((VProxyInetSocketChannel) channel);
            } else if (channel instanceof VProxyDatagramFDChannel) {
                registerDatagramFD((VProxyDatagramFDChannel) channel);
            } else if (channel instanceof VProxyServerSockChannel) {
                registerServerSock((VProxyServerSockChannel) channel);
            } else if (channel instanceof VProxyInetServerSocketChannel) {
                registerWrapServerSock((VProxyInetServerSocketChannel) channel);
            } else {
                throw new IllegalArgumentException("unable to determine vproxy net object from channel: " + channel);
            }
        } catch (Throwable t) {
            // error had been logged in the above functions
            afterRegister(channel, true);
            promise.setFailure(t);
            return promise;
        }

        afterRegister(channel, false);
        promise.setSuccess();
        return promise;
    }

    protected void beforeRegister(Channel channel) {
        ((AbstractVProxyChannel) channel).__temporaryEventLoop = this;

        // this must happen before real registering
        // because when it's registered, readable events will begin to fire
        // but netty might not have initialized the channel because
        // fireChannelRegistered is not called
        channel.pipeline().fireChannelRegistered();
    }

    protected void afterRegister(Channel channel, boolean failed) {
        if (failed) {
            channel.pipeline().fireChannelUnregistered();
        }
        ((AbstractVProxyChannel) channel).__temporaryEventLoop = null;
    }

    private void registerConnection(VProxyConnectionChannel channel) throws IOException {
        var connection = channel.connection();
        try {
            if (connection instanceof ConnectableConnection) {
                el.addConnectableConnection((ConnectableConnection) connection, null, channel.createConnectionHandler());
            } else {
                el.addConnection(connection, null, channel.createConnectionHandler());
            }
        } catch (IOException e) {
            Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "failed adding " + connection + " to eventloop " + el, e);
            throw e;
        }
    }

    private void registerDatagramFD(VProxyDatagramFDChannel channel) throws IOException {
        var fd = channel.datagramFD();
        try {
            el.getSelectorEventLoop().add(fd, EventSet.read(), null, channel.createFDHandler());
        } catch (IOException e) {
            Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "failed adding datagram " + fd + " to eventloop " + el, e);
            throw e;
        }
        channel.setEventLoop(el);
    }

    private void registerServerSock(VProxyServerSockChannel channel) throws IOException {
        var serverSock = channel.serverSock();
        try {
            el.addServer(serverSock, null, channel.createServerHandler());
        } catch (IOException e) {
            Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "failed adding server " + serverSock + " to eventloop " + el, e);
            throw e;
        }
    }

    private void registerWrapServerSock(VProxyInetServerSocketChannel channel) throws AlreadyExistException {
        channel.register(this);
    }

    private void registerWrapSock(VProxyInetSocketChannel channel) throws AlreadyExistException {
        channel.register(this);
    }

    @Override
    public boolean isShuttingDown() {
        return el.getSelectorEventLoop().isClosed();
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

    @Override
    public void shutdown() {
        try {
            el.getSelectorEventLoop().close();
        } catch (IOException e) {
            Logger.error(LogType.EVENT_LOOP_CLOSE_FAIL, "failed to close event loop", e);
            // fallthrough
        }
        terminationFuture.setSuccess(null);
    }

    @Override
    public boolean isShutdown() {
        return el.getSelectorEventLoop().isClosed();
    }

    @Override
    public boolean isTerminated() {
        return el.getSelectorEventLoop().isClosed();
    }

    @Blocking
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        shutdown();
        return true;
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return el.getSelectorEventLoop().getRunningThread() == thread;
    }

    @Override
    public void execute(Runnable command) {
        el.getSelectorEventLoop().nextTick(command);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        int iDelay = (int) unit.convert(delay, TimeUnit.MILLISECONDS);
        var f = new VProxyEventLoopDelayScheduledFuture<>(iDelay);
        var event = el.getSelectorEventLoop().delay(iDelay, () -> {
            try {
                command.run();
            } catch (Throwable t) {
                f.setFailure(t);
                return;
            }
            f.setSuccess(null);
        });
        f.setEvent(event);
        return f;
    }
}
