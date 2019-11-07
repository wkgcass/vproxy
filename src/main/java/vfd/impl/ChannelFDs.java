package vfd.impl;

import vfd.FDSelector;
import vfd.FDs;
import vfd.ServerSocketFD;
import vfd.SocketFD;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class ChannelFDs implements FDs {
    @Override
    public SocketFD openSocketFD() throws IOException {
        return new SocketChannelFD(SocketChannel.open());
    }

    @Override
    public ServerSocketFD openServerSocketFD() throws IOException {
        return new ServerSocketChannelFD(ServerSocketChannel.open());
    }

    @Override
    public FDSelector openSelector() throws IOException {
        return new ChannelSelector(Selector.open());
    }
}
