package io.vproxy.adaptor.netty.channel;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.ServerSocketChannelConfig;

public class VProxyServerSockChannelConfig extends VProxyChannelConfig implements ServerSocketChannelConfig {
    private final VProxyServerSockChannel.Config config;

    public VProxyServerSockChannelConfig(Channel channel, VProxyServerSockChannel.Config config) {
        super(channel);
        this.config = config;
    }

    protected VProxyServerSockChannelConfig(Channel channel, RecvByteBufAllocator allocator, VProxyServerSockChannel.Config config) {
        super(channel, allocator);
        this.config = config;
    }

    @Override
    public ServerSocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        return (ServerSocketChannelConfig) super.setConnectTimeoutMillis(connectTimeoutMillis);
    }

    @Override
    public ServerSocketChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        return (ServerSocketChannelConfig) super.setMaxMessagesPerRead(maxMessagesPerRead);
    }

    @Override
    public ServerSocketChannelConfig setWriteSpinCount(int writeSpinCount) {
        return (ServerSocketChannelConfig) super.setWriteSpinCount(writeSpinCount);
    }

    @Override
    public ServerSocketChannelConfig setAllocator(ByteBufAllocator allocator) {
        return (ServerSocketChannelConfig) super.setAllocator(allocator);
    }

    @Override
    public ServerSocketChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        return (ServerSocketChannelConfig) super.setRecvByteBufAllocator(allocator);
    }

    @Override
    public ServerSocketChannelConfig setAutoRead(boolean autoRead) {
        return (ServerSocketChannelConfig) super.setAutoRead(autoRead);
    }

    @Override
    public ServerSocketChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        return (ServerSocketChannelConfig) super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
    }

    @Override
    public ServerSocketChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        return (ServerSocketChannelConfig) super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
    }

    @Override
    public ServerSocketChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        return (ServerSocketChannelConfig) super.setWriteBufferWaterMark(writeBufferWaterMark);
    }

    @Override
    public ServerSocketChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        return (ServerSocketChannelConfig) super.setMessageSizeEstimator(estimator);
    }

    @Override
    public int getBacklog() {
        return 0;
    }

    @Override
    public ServerSocketChannelConfig setBacklog(int backlog) {
        return this;
    }

    @Override
    public boolean isReuseAddress() {
        return false;
    }

    @Override
    public ServerSocketChannelConfig setReuseAddress(boolean reuseAddress) {
        return this;
    }

    @Override
    public int getReceiveBufferSize() {
        return 0;
    }

    @Override
    public ServerSocketChannelConfig setReceiveBufferSize(int receiveBufferSize) {
        return this;
    }

    @Override
    public ServerSocketChannelConfig setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        return this;
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        if (option == ChannelOption.SO_REUSEADDR) {
            return true;
        }
        if (option.toString().equals("SO_REUSEPORT")) {
            return true;
        }
        return super.setOption(option, value);
    }
}
