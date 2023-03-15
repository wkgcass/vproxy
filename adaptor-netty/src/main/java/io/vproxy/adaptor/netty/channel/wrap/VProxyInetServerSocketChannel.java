package io.vproxy.adaptor.netty.channel.wrap;

import io.netty.channel.*;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.ServerSocketChannelConfig;
import io.vproxy.adaptor.netty.channel.VProxyEventLoop;
import io.vproxy.adaptor.netty.channel.VProxyServerSockChannel;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.connection.ServerSock;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.adaptor.netty.channel.AbstractVProxyChannel;
import io.vproxy.adaptor.netty.channel.VProxyServerSockChannelConfig;
import io.vproxy.vfd.IPPort;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class VProxyInetServerSocketChannel extends AbstractVProxyChannel implements ServerSocketChannel {
    private VProxyServerSockChannel delegate;
    private VProxyEventLoop loop = null;
    private final VProxyServerSockChannel.Config config = new VProxyServerSockChannel.Config();

    public VProxyInetServerSocketChannel() {
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
    public ServerSocketChannelConfig config() {
        return (ServerSocketChannelConfig) super.config();
    }

    @Override
    protected ChannelConfig config0() {
        return new VProxyServerSockChannelConfig(this, config);
    }

    private VProxyServerSockChannel ensureDelegate() {
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
    public Channel parent() {
        return ensureDelegate().parent();
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

    private class Unsafe extends DelegateUnsafe implements Channel.Unsafe {
        private Unsafe() {
            super(VProxyInetServerSocketChannel.this);
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
            if (delegate != null) {
                promise.setFailure(new IllegalStateException("already bond"));
                return;
            }
            ServerSock sock;
            try {
                sock = ServerSock.create(IPPort.from(localAddress));
            } catch (IOException e) {
                promise.setFailure(e);
                return;
            }
            delegate = new VProxyServerSockChannel(sock) {
                @Override
                public ChannelId id() {
                    return VProxyInetServerSocketChannel.this.id();
                }

                @Override
                public ChannelPipeline pipeline() {
                    return VProxyInetServerSocketChannel.this.pipeline();
                }

                @Override
                public ChannelMetadata metadata() {
                    return VProxyInetServerSocketChannel.this.metadata();
                }

                @Override
                public ChannelConfig config() {
                    return VProxyInetServerSocketChannel.this.config();
                }

                @Override
                protected Config config1() {
                    return config;
                }

                @Override
                protected DefaultChannelPromise closeFuture0() {
                    return VProxyInetServerSocketChannel.this.closeFuture0();
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

        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
        }
    }
}
