package io.vproxy.adaptor.netty.channel.wrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramChannelConfig;
import io.vproxy.adaptor.netty.channel.VProxyDatagramFDChannel;
import io.vproxy.vfd.DatagramFD;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;

public class VProxyDatagramChannel extends VProxyDatagramFDChannel implements DatagramChannel {
    private boolean isConnected = false;

    public VProxyDatagramChannel() throws IOException {
    }

    public VProxyDatagramChannel(DatagramFD fd) {
        super(fd);
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public DatagramChannelConfig config() {
        return (DatagramChannelConfig) super.config();
    }

    @Override
    protected Channel.Unsafe unsafe0() {
        return new Unsafe();
    }

    @Override
    public boolean isConnected() {
        //noinspection resource
        return isConnected && datagramFD().isOpen();
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress) {
        return joinGroup(multicastAddress, newPromise());
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress, ChannelPromise future) {
        future.setFailure(new UnsupportedOperationException());
        return future;
    }

    @Override
    public ChannelFuture joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
        return joinGroup(multicastAddress, networkInterface, newPromise());
    }

    @Override
    public ChannelFuture joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface, ChannelPromise future) {
        future.setFailure(new UnsupportedOperationException());
        return future;
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        return joinGroup(multicastAddress, networkInterface, source, newPromise());
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source, ChannelPromise future) {
        future.setFailure(new UnsupportedOperationException());
        return future;
    }

    @Override
    public ChannelFuture leaveGroup(InetAddress multicastAddress) {
        return leaveGroup(multicastAddress, newPromise());
    }

    @Override
    public ChannelFuture leaveGroup(InetAddress multicastAddress, ChannelPromise future) {
        future.setFailure(new UnsupportedOperationException());
        return future;
    }

    @Override
    public ChannelFuture leaveGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
        return leaveGroup(multicastAddress, networkInterface, newPromise());
    }

    @Override
    public ChannelFuture leaveGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface, ChannelPromise future) {
        future.setFailure(new UnsupportedOperationException());
        return future;
    }

    @Override
    public ChannelFuture leaveGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        return leaveGroup(multicastAddress, networkInterface, source, newPromise());
    }

    @Override
    public ChannelFuture leaveGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source, ChannelPromise future) {
        future.setFailure(new UnsupportedOperationException());
        return future;
    }

    @Override
    public ChannelFuture block(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress sourceToBlock) {
        return block(multicastAddress, networkInterface, sourceToBlock, newPromise());
    }

    @Override
    public ChannelFuture block(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress sourceToBlock, ChannelPromise future) {
        future.setFailure(new UnsupportedOperationException());
        return future;
    }

    @Override
    public ChannelFuture block(InetAddress multicastAddress, InetAddress sourceToBlock) {
        return block(multicastAddress, sourceToBlock, newPromise());
    }

    @Override
    public ChannelFuture block(InetAddress multicastAddress, InetAddress sourceToBlock, ChannelPromise future) {
        future.setFailure(new UnsupportedOperationException());
        return future;
    }

    protected class Unsafe extends VProxyDatagramFDChannel.Unsafe {
        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            var p = newPromise();
            p.addListener(f -> {
                if (f.cause() != null) {
                    promise.setFailure(f.cause());
                    return;
                }
                isConnected = true;
                promise.setSuccess();
            });
            super.connect(remoteAddress, localAddress, p);
        }
    }
}
