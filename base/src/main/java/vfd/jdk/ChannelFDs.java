package vfd.jdk;

import vfd.*;

import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class ChannelFDs implements FDs {
    private static final ChannelFDs instance = new ChannelFDs();

    public static ChannelFDs get() {
        return instance;
    }

    private ChannelFDs() {
    }

    @Override
    public SocketFD openSocketFD() throws IOException {
        return new SocketChannelFD(SocketChannel.open());
    }

    @Override
    public ServerSocketFD openServerSocketFD() throws IOException {
        return new ServerSocketChannelFD(ServerSocketChannel.open());
    }

    @Override
    public DatagramFD openDatagramFD() throws IOException {
        return new DatagramChannelFD(DatagramChannel.open());
    }

    @Override
    public FDSelector openSelector() throws IOException {
        return new ChannelSelector(Selector.open());
    }

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
