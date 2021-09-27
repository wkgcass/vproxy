package io.vproxy.vfd.jdk;

import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.SocketFD;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class SocketChannelFD extends ChannelFD implements SocketFD {
    private final SocketChannel channel;

    public SocketChannelFD(SocketChannel channel) {
        super(channel);
        this.channel = channel;
    }

    @Override
    public void configureBlocking(boolean b) throws IOException {
        channel.configureBlocking(b);
    }

    @Override
    public void connect(IPPort l4addr) throws IOException {
        channel.connect(l4addr.toInetSocketAddress());
    }

    @Override
    public IPPort getLocalAddress() throws IOException {
        return IPPort.fromNullable(channel.getLocalAddress());
    }

    @Override
    public IPPort getRemoteAddress() throws IOException {
        return IPPort.fromNullable(channel.getRemoteAddress());
    }

    @Override
    public boolean finishConnect() throws IOException {
        return channel.finishConnect();
    }

    @Override
    public boolean isConnected() {
        return channel.isConnected();
    }

    @Override
    public void shutdownOutput() throws IOException {
        channel.shutdownOutput();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return channel.read(dst);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return channel.write(src);
    }

    @Override
    public SocketChannel getChannel() {
        return channel;
    }

    @Override
    public String toString() {
        return "SocketChannelFD(" + channel + ")";
    }
}
