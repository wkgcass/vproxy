package vfd.impl;

import vfd.FD;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.NetworkChannel;
import java.nio.channels.SelectableChannel;

public class ChannelFDBase implements FD {
    private final SelectableChannel channel;

    public ChannelFDBase(SelectableChannel channel) {
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

    public SelectableChannel getChannel() {
        return channel;
    }
}
