package net.cassite.vproxy.component.svrgroup;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class Connector {
    public final InetSocketAddress remote;
    public final InetAddress local;

    public Connector(InetSocketAddress remote, InetAddress local) {
        this.remote = remote;
        this.local = local;
    }

    public SocketChannel connect() throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.bind(new InetSocketAddress(local, 0));
        channel.connect(remote);
        return channel;
    }

    @Override
    public String toString() {
        return "Connector(remote(" + remote + "), local(" + local + "))";
    }
}
