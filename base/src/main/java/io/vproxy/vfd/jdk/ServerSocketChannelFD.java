package io.vproxy.vfd.jdk;

import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.ServerSocketFD;
import io.vproxy.vfd.SocketFD;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class ServerSocketChannelFD extends ChannelFD implements ServerSocketFD {
    private final ServerSocketChannel channel;

    public ServerSocketChannelFD(ServerSocketChannel channel) {
        super(channel);
        this.channel = channel;
    }

    @Override
    public IPPort getLocalAddress() throws IOException {
        return IPPort.fromNullable(channel.getLocalAddress());
    }

    @Override
    public SocketFD accept() throws IOException {
        SocketChannel socket = channel.accept();
        if (socket == null) {
            return null;
        }
        return new SocketChannelFD(socket);
    }

    @Override
    public void bind(IPPort l4addr) throws IOException {
        channel.bind(l4addr.toInetSocketAddress(), 512);
    }

    @Override
    public ServerSocketChannel getChannel() {
        return channel;
    }
}
