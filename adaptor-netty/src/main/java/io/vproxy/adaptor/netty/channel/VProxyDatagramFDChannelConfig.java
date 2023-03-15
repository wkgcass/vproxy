package io.vproxy.adaptor.netty.channel;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannelConfig;

import java.net.InetAddress;
import java.net.NetworkInterface;

public class VProxyDatagramFDChannelConfig extends VProxyChannelConfig implements DatagramChannelConfig {
    private final VProxyDatagramFDChannel.Config config;

    public VProxyDatagramFDChannelConfig(Channel channel, VProxyDatagramFDChannel.Config config) {
        super(channel);
        this.config = config;
    }

    protected VProxyDatagramFDChannelConfig(Channel channel, RecvByteBufAllocator allocator, VProxyDatagramFDChannel.Config config) {
        super(channel, allocator);
        this.config = config;
    }

    @Override
    public DatagramChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        return (DatagramChannelConfig) super.setConnectTimeoutMillis(connectTimeoutMillis);
    }

    @Override
    public DatagramChannelConfig setMaxMessagesPerWrite(int maxMessagesPerWrite) {
        return (DatagramChannelConfig) super.setMaxMessagesPerWrite(maxMessagesPerWrite);
    }

    @Override
    public DatagramChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        return (DatagramChannelConfig) super.setMaxMessagesPerRead(maxMessagesPerRead);
    }

    @Override
    public DatagramChannelConfig setWriteSpinCount(int writeSpinCount) {
        return (DatagramChannelConfig) super.setWriteSpinCount(writeSpinCount);
    }

    @Override
    public DatagramChannelConfig setAllocator(ByteBufAllocator allocator) {
        return (DatagramChannelConfig) super.setAllocator(allocator);
    }

    @Override
    public DatagramChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        return (DatagramChannelConfig) super.setRecvByteBufAllocator(allocator);
    }

    @Override
    public DatagramChannelConfig setAutoRead(boolean autoRead) {
        return (DatagramChannelConfig) super.setAutoRead(autoRead);
    }

    @Override
    public DatagramChannelConfig setAutoClose(boolean autoClose) {
        return (DatagramChannelConfig) super.setAutoClose(autoClose);
    }

    @Override
    public DatagramChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        return (DatagramChannelConfig) super.setWriteBufferWaterMark(writeBufferWaterMark);
    }

    @Override
    public DatagramChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        return (DatagramChannelConfig) super.setMessageSizeEstimator(estimator);
    }

    @Override
    public int getSendBufferSize() {
        return 0;
    }

    @Override
    public DatagramChannelConfig setSendBufferSize(int sendBufferSize) {
        return this;
    }

    @Override
    public int getReceiveBufferSize() {
        return 0;
    }

    @Override
    public DatagramChannelConfig setReceiveBufferSize(int receiveBufferSize) {
        return this;
    }

    @Override
    public int getTrafficClass() {
        return 0;
    }

    @Override
    public DatagramChannelConfig setTrafficClass(int trafficClass) {
        return this;
    }

    @Override
    public boolean isReuseAddress() {
        return true;
    }

    @Override
    public DatagramChannelConfig setReuseAddress(boolean reuseAddress) {
        return this;
    }

    @Override
    public boolean isBroadcast() {
        return false;
    }

    @Override
    public DatagramChannelConfig setBroadcast(boolean broadcast) {
        return this;
    }

    @Override
    public boolean isLoopbackModeDisabled() {
        return false;
    }

    @Override
    public DatagramChannelConfig setLoopbackModeDisabled(boolean loopbackModeDisabled) {
        return this;
    }

    @Override
    public int getTimeToLive() {
        return 0;
    }

    @Override
    public DatagramChannelConfig setTimeToLive(int ttl) {
        return this;
    }

    @Override
    public InetAddress getInterface() {
        return null;
    }

    @Override
    public DatagramChannelConfig setInterface(InetAddress interfaceAddress) {
        return this;
    }

    @Override
    public NetworkInterface getNetworkInterface() {
        return null;
    }

    @Override
    public DatagramChannelConfig setNetworkInterface(NetworkInterface networkInterface) {
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
        //noinspection deprecation
        if (option.equals(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION)) {
            return true;
        }
        return super.setOption(option, value);
    }
}
