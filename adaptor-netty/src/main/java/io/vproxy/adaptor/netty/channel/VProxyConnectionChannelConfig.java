package io.vproxy.adaptor.netty.channel;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannelConfig;

public class VProxyConnectionChannelConfig extends VProxyChannelConfig implements SocketChannelConfig {
    private final VProxyConnectionChannel.Config config;

    public VProxyConnectionChannelConfig(Channel channel, VProxyConnectionChannel.Config config) {
        super(channel);
        this.config = config;
    }

    protected VProxyConnectionChannelConfig(Channel channel, RecvByteBufAllocator allocator, VProxyConnectionChannel.Config config) {
        super(channel, allocator);
        this.config = config;
    }

    @Override
    public SocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        return (SocketChannelConfig) super.setConnectTimeoutMillis(connectTimeoutMillis);
    }

    @Override
    public SocketChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        return (SocketChannelConfig) super.setMaxMessagesPerRead(maxMessagesPerRead);
    }

    @Override
    public SocketChannelConfig setWriteSpinCount(int writeSpinCount) {
        return (SocketChannelConfig) super.setWriteSpinCount(writeSpinCount);
    }

    @Override
    public SocketChannelConfig setAllocator(ByteBufAllocator allocator) {
        return (SocketChannelConfig) super.setAllocator(allocator);
    }

    @Override
    public SocketChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        return (SocketChannelConfig) super.setRecvByteBufAllocator(allocator);
    }

    @Override
    public SocketChannelConfig setAutoRead(boolean autoRead) {
        return (SocketChannelConfig) super.setAutoRead(autoRead);
    }

    @Override
    public SocketChannelConfig setAutoClose(boolean autoClose) {
        return (SocketChannelConfig) super.setAutoClose(autoClose);
    }

    @Override
    public SocketChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        return (SocketChannelConfig) super.setWriteBufferWaterMark(writeBufferWaterMark);
    }

    @Override
    public SocketChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        return (SocketChannelConfig) super.setMessageSizeEstimator(estimator);
    }

    @Override
    public boolean isTcpNoDelay() {
        return true;
    }

    @Override
    public SocketChannelConfig setTcpNoDelay(boolean tcpNoDelay) {
        return this;
    }

    private int solinger = -1;

    @Override
    public int getSoLinger() {
        return solinger;
    }

    @Override
    public SocketChannelConfig setSoLinger(int soLinger) {
        return this;
    }

    @Override
    public int getSendBufferSize() {
        return 0;
    }

    @Override
    public SocketChannelConfig setSendBufferSize(int sendBufferSize) {
        return this;
    }

    @Override
    public int getReceiveBufferSize() {
        return 0;
    }

    @Override
    public SocketChannelConfig setReceiveBufferSize(int receiveBufferSize) {
        return this;
    }

    @Override
    public boolean isKeepAlive() {
        return false;
    }

    @Override
    public SocketChannelConfig setKeepAlive(boolean keepAlive) {
        return this;
    }

    @Override
    public int getTrafficClass() {
        return 0;
    }

    @Override
    public SocketChannelConfig setTrafficClass(int trafficClass) {
        return this;
    }

    @Override
    public boolean isReuseAddress() {
        return false;
    }

    @Override
    public SocketChannelConfig setReuseAddress(boolean reuseAddress) {
        return this;
    }

    @Override
    public SocketChannelConfig setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        return this;
    }

    @Override
    public boolean isAllowHalfClosure() {
        return config.isAllowHalfClosure();
    }

    @Override
    public SocketChannelConfig setAllowHalfClosure(boolean allowHalfClosure) {
        config.setAllowHalfClosure(allowHalfClosure);
        return this;
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        if (option == ChannelOption.SO_KEEPALIVE) {
            return true;
        }
        if (option == ChannelOption.TCP_NODELAY) {
            return true;
        }
        if (option == ChannelOption.SO_REUSEADDR) {
            return true;
        }
        if (option.toString().equals("SO_REUSEPORT")) {
            return true;
        }
        if (option == ChannelOption.SO_LINGER) {
            Integer n = (Integer) value;
            config.setReset(n != null && n == 0);
            if (n == null || n < 0) {
                n = -1;
            }
            solinger = n;
            return true;
        }
        return super.setOption(option, value);
    }
}
