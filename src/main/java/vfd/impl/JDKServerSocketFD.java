package vfd.impl;

import vfd.ServerSocketFD;
import vfd.SocketFD;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class JDKServerSocketFD extends ChannelFDBase implements ServerSocketFD {
    private final ServerSocketChannel channel;

    public JDKServerSocketFD(ServerSocketChannel channel) {
        super(channel);
        this.channel = channel;
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return channel.getLocalAddress();
    }

    @Override
    public SocketFD accept() throws IOException {
        SocketChannel socket = channel.accept();
        if (socket == null) {
            return null;
        }
        return new JDKSocketFD(socket);
    }

    @Override
    public void bind(InetSocketAddress l4addr) throws IOException {
        channel.bind(l4addr);
    }

    @Override
    public ServerSocketChannel getChannel() {
        return channel;
    }
}
