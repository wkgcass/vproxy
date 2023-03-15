package io.vproxy.adaptor.netty.channel;

import io.netty.channel.*;
import io.vproxy.base.connection.*;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.SocketFD;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Objects;

public class VProxyServerSockChannel extends AbstractVProxyChannel implements ServerChannel {
    private final ServerSock sock;
    private Config __config;

    public VProxyServerSockChannel(ServerSock sock) {
        Objects.requireNonNull(sock);
        this.sock = sock;
    }

    public static VProxyServerSockChannel create(IPPort ipport) {
        try {
            return new VProxyServerSockChannel(ServerSock.create(ipport));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ServerSock serverSock() {
        return sock;
    }

    @Override
    protected NetEventLoop eventLoop0() {
        return sock.getEventLoop();
    }

    @Override
    public Channel parent() {
        return null;
    }

    @Override
    protected ChannelConfig config0() {
        return new VProxyServerSockChannelConfig(this, config1());
    }

    protected Config config1() {
        if (__config == null) {
            return __config = new Config();
        }
        return __config;
    }

    @Override
    public boolean isWritable() {
        return false;
    }

    @Override
    public long bytesBeforeUnwritable() {
        return 0;
    }

    @Override
    public long bytesBeforeWritable() {
        return 0;
    }

    @Override
    protected Channel.Unsafe unsafe0() {
        return new Unsafe();
    }

    @Override
    public boolean isOpen() {
        return !sock.isClosed();
    }

    @Override
    public boolean isActive() {
        return !sock.isClosed();
    }

    protected int connectionChannelInBufferSize() {
        return 24576;
    }

    protected int connectionChannelOutBufferSize() {
        return 24576;
    }

    protected ServerHandler createServerHandler() {
        return new Handler();
    }

    protected class Handler implements ServerHandler {
        @Override
        public void acceptFail(ServerHandlerContext ctx, IOException err) {
            Logger.error(LogType.SERVER_ACCEPT_FAIL, "failed to accept from serverSock " + sock, err);
            pipeline().fireExceptionCaught(err);
        }

        @Override
        public void connection(ServerHandlerContext ctx, Connection connection) {
            pipeline().fireChannelRead(new VProxyConnectionChannel(VProxyServerSockChannel.this, connection));
        }

        @Override
        public Tuple<RingBuffer, RingBuffer> getIOBuffers(SocketFD channel) {
            return new Tuple<>(
                RingBuffer.allocateDirect(connectionChannelInBufferSize()),
                RingBuffer.allocateDirect(connectionChannelOutBufferSize())
            );
        }

        @Override
        public void removed(ServerHandlerContext ctx) {
            Logger.warn(LogType.ALERT, "serverSock " + sock + " removed from eventloop");
            ctx.eventLoop.getSelectorEventLoop().nextTick(() ->
                pipeline().fireChannelUnregistered());
        }
    }

    protected class Unsafe extends AbstractVProxyUnsafe implements Channel.Unsafe {
        public Unsafe() {
            super(VProxyServerSockChannel.this);
        }

        @Override
        public SocketAddress localAddress() {
            return sock.bind.toInetSocketAddress();
        }

        @Override
        public SocketAddress remoteAddress() {
            return null;
        }

        @Override
        public void close(ChannelPromise promise) {
            sock.close();
            promise.setSuccess();
            closeFuture0().setSuccess();
        }

        @Override
        public void deregister(ChannelPromise promise) {
            var el = sock.getEventLoop();
            if (el != null) {
                el.removeServer(sock);
            }
            promise.setSuccess();
        }

        @Override
        public void write(Object msg, ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
        }
    }

    public static class Config {
    }
}
