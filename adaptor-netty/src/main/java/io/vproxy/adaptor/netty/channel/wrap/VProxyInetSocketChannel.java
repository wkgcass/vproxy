package io.vproxy.adaptor.netty.channel.wrap;

import io.netty.channel.*;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketChannelConfig;
import io.vproxy.adaptor.netty.channel.VProxyEventLoop;
import io.vproxy.base.connection.ConnectableConnection;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.adaptor.netty.channel.AbstractVProxyChannel;
import io.vproxy.adaptor.netty.channel.VProxyConnectionChannel;
import io.vproxy.adaptor.netty.channel.VProxyConnectionChannelConfig;
import io.vproxy.vfd.UnixDomainSocketAddress;
import io.vproxy.vfd.IPPort;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class VProxyInetSocketChannel extends AbstractVProxyChannel implements SocketChannel {
    private VProxyConnectionChannel delegate;
    private VProxyEventLoop loop;
    private final VProxyConnectionChannel.Config config = new VProxyConnectionChannel.Config();

    public VProxyInetSocketChannel() {
    }

    @SuppressWarnings("DuplicatedCode")
    public void register(VProxyEventLoop loop) throws AlreadyExistException {
        if (this.loop != null) {
            throw new AlreadyExistException("loop");
        }
        this.loop = loop;
        if (this.delegate != null) {
            loop.register(delegate).addListener(f -> {
                if (f.cause() != null) {
                    Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "failed adding " + delegate + " to " + loop + " in delegate register method");
                }
            });
        }
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public EventLoop eventLoop() {
        if (delegate == null) return super.eventLoop();
        return delegate.eventLoop();
    }

    @Override
    protected NetEventLoop eventLoop0() {
        if (loop == null) return null;
        return loop.getNetEventLoop();
    }

    @Override
    public SocketChannelConfig config() {
        return (SocketChannelConfig) super.config();
    }

    @Override
    protected ChannelConfig config0() {
        return new VProxyConnectionChannelConfig(this, config);
    }

    private VProxyConnectionChannel ensureDelegate() {
        if (delegate == null) {
            throw new IllegalStateException();
        }
        return delegate;
    }

    @Override
    protected Channel.Unsafe unsafe0() {
        return new Unsafe();
    }

    @Override
    public ServerSocketChannel parent() {
        return (ServerSocketChannel) ensureDelegate().parent();
    }

    @Override
    public boolean isOpen() {
        return ensureDelegate().isOpen();
    }

    @Override
    public boolean isActive() {
        return ensureDelegate().isActive();
    }

    @Override
    public boolean isWritable() {
        return ensureDelegate().isWritable();
    }

    @Override
    public long bytesBeforeUnwritable() {
        return ensureDelegate().bytesBeforeUnwritable();
    }

    @Override
    public long bytesBeforeWritable() {
        return ensureDelegate().bytesBeforeWritable();
    }

    @Override
    public boolean isInputShutdown() {
        return ensureDelegate().isInputShutdown();
    }

    @Override
    public ChannelFuture shutdownInput() {
        return ensureDelegate().shutdownInput();
    }

    @Override
    public ChannelFuture shutdownInput(ChannelPromise promise) {
        return ensureDelegate().shutdownInput(promise);
    }

    @Override
    public boolean isOutputShutdown() {
        return ensureDelegate().isOutputShutdown();
    }

    @Override
    public ChannelFuture shutdownOutput() {
        return ensureDelegate().shutdownOutput();
    }

    @Override
    public ChannelFuture shutdownOutput(ChannelPromise promise) {
        return ensureDelegate().shutdownOutput(promise);
    }

    @Override
    public boolean isShutdown() {
        return ensureDelegate().isShutdown();
    }

    @Override
    public ChannelFuture shutdown() {
        return ensureDelegate().shutdown();
    }

    @Override
    public ChannelFuture shutdown(ChannelPromise promise) {
        return ensureDelegate().shutdown(promise);
    }

    protected class Unsafe extends DelegateUnsafe implements Channel.Unsafe {
        protected Unsafe() {
            super(VProxyInetSocketChannel.this);
        }

        @Override
        protected Channel delegate() {
            return delegate;
        }

        @Override
        protected void unsetLoop() {
            loop = null;
        }

        @Override
        public void bind(SocketAddress localAddress, ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException("should call connect(remote, local, promise)"));
        }

        private boolean isConcreteAddress(SocketAddress addr) {
            if (addr == null) {
                return false;
            }
            if (!(addr instanceof InetSocketAddress)) {
                return true; // only able to handle InetSocketAddress
            }
            var inet = (InetSocketAddress) addr;
            if (inet instanceof UnixDomainSocketAddress) {
                return true;
            }
            if (inet.getPort() != 0) {
                return true;
            }
            var ip = inet.getAddress();
            if (ip == null) {
                return false;
            }
            var bytes = ip.getAddress();
            for (var b : bytes) {
                if (b != 0) {
                    return true;
                }
            }
            // all 0
            return false;
        }

        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            if (delegate != null) {
                promise.setFailure(new IllegalStateException("already connected"));
                return;
            }
            if (isConcreteAddress(localAddress)) {
                Logger.warn(LogType.IMPROPER_USE, "calling connect(" + remoteAddress + ", " + localAddress + ", promise) but binding is currently not supported, ignoring localAddress " + localAddress + " and continue");
            }
            ConnectableConnection conn;
            try {
                conn = ConnectableConnection.create(IPPort.from(remoteAddress));
            } catch (IOException e) {
                promise.setFailure(e);
                return;
            }
            delegate = new VProxyConnectionChannel(conn) {
                @Override
                public ChannelId id() {
                    return VProxyInetSocketChannel.this.id();
                }

                @Override
                public ChannelPipeline pipeline() {
                    return VProxyInetSocketChannel.this.pipeline();
                }

                @Override
                public ChannelMetadata metadata() {
                    return VProxyInetSocketChannel.this.metadata();
                }

                @Override
                public ChannelConfig config() {
                    return VProxyInetSocketChannel.this.config();
                }

                @Override
                protected Config config1() {
                    return config;
                }

                @Override
                protected DefaultChannelPromise closeFuture0() {
                    return VProxyInetSocketChannel.this.closeFuture0();
                }
            };
            if (loop != null) {
                loop.register(delegate, promise).addListener(f -> {
                    if (f.cause() != null) {
                        delegate.close();
                    }
                });
            }
        }
    }
}
