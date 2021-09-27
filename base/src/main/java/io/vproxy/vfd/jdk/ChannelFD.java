package io.vproxy.vfd.jdk;

import io.vproxy.vfd.FD;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.NetworkChannel;
import java.nio.channels.SelectableChannel;

public class ChannelFD implements FD {
    private final SelectableChannel channel;

    public ChannelFD(SelectableChannel channel) {
        this.channel = channel;
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    @Override
    public void configureBlocking(boolean b) throws IOException {
        channel.configureBlocking(b);
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        ((NetworkChannel) channel).setOption(name, value);
    }

    @Override
    public FD real() {
        return this;
    }

    @Override
    public boolean contains(FD fd) {
        return false;
    }

    public SelectableChannel getChannel() {
        return channel;
    }
}
