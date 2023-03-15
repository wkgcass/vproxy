package io.vproxy.adaptor.vertx;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.vertx.core.spi.transport.Transport;
import io.vproxy.base.util.Logger;
import io.vproxy.adaptor.netty.channel.wrap.VProxyDatagramChannel;
import io.vproxy.adaptor.netty.channel.VProxyEventLoopGroup;
import io.vproxy.adaptor.netty.channel.wrap.VProxyInetServerSocketChannel;
import io.vproxy.adaptor.netty.channel.wrap.VProxyInetSocketChannel;

import java.io.IOException;
import java.util.concurrent.ThreadFactory;

public class VProxyTransport implements Transport {
    public VProxyTransport() {
        Logger.alert("using VProxyTransport for Vert.x");
    }

    @Override
    public EventLoopGroup eventLoopGroup(int type, int nThreads, ThreadFactory threadFactory, int ioRatio) {
        assert Logger.lowLevelDebug("vproxy eventLoopGroup(type=" + type + ", nThreads=" + nThreads + ", threadFactory=" + threadFactory + ", ioRatio=" + ioRatio + ")");
        try {
            return new VProxyEventLoopGroup(nThreads);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DatagramChannel datagramChannel() {
        assert Logger.lowLevelDebug("vproxy datagramChannel()");
        try {
            return new VProxyDatagramChannel();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DatagramChannel datagramChannel(InternetProtocolFamily family) {
        assert Logger.lowLevelDebug("vproxy datagramChannel(family=" + family + ")");
        return datagramChannel();
    }

    @Override
    public ChannelFactory<? extends Channel> channelFactory(boolean domainSocket) {
        assert Logger.lowLevelDebug("vproxy channelFactory(domainSocket=" + domainSocket + ")");
        if (domainSocket) {
            throw new UnsupportedOperationException("please use a normal socket and pass in io.vproxy.netty.util.UnixDomainSocketAddress when connecting a domain socket");
        }
        return VProxyInetSocketChannel::new;
    }

    @Override
    public ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domainSocket) {
        assert Logger.lowLevelDebug("vproxy serverChannelFactory(domainSocket=" + domainSocket + ")");
        if (domainSocket) {
            throw new UnsupportedOperationException("please use a normal socket and pass in io.vproxy.netty.util.UnixDomainSocketAddress when binding the domain socket");
        }
        return VProxyInetServerSocketChannel::new;
    }
}
