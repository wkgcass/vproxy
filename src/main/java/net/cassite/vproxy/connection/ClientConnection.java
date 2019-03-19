package net.cassite.vproxy.connection;

import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.Utils;

import java.io.IOException;
import java.net.*;
import java.nio.channels.SocketChannel;

public class ClientConnection extends Connection {
    Connector connector; // maybe null, only for recording purpose, will not be used by the connection lib

    public Connector getConnector() {
        return connector;
    }

    public static ClientConnection create(InetSocketAddress remote, InetAddress local,
                                          RingBuffer inBuffer, RingBuffer outBuffer) throws IOException {
        return create(remote, new InetSocketAddress(local, 0), inBuffer, outBuffer);
    }

    public static ClientConnection create(InetSocketAddress remote, InetSocketAddress local,
                                          RingBuffer inBuffer, RingBuffer outBuffer) throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        channel.bind(local);
        channel.connect(remote);
        try {
            return new ClientConnection(channel, remote, local, inBuffer, outBuffer);
        } catch (IOException e) {
            channel.close(); // close the channel if create ClientConnection failed
            throw e;
        }
    }

    private ClientConnection(SocketChannel channel,
                             InetSocketAddress remote, InetSocketAddress local,
                             RingBuffer inBuffer, RingBuffer outBuffer) throws IOException {
        super(channel, remote, local, inBuffer, outBuffer);
    }

    @Override
    protected String genId() {
        return (local == null ? "[unbound]" :
            (
                Utils.ipStr(local.getAddress().getAddress()) + ":" + local.getPort()
            ))
            + "/"
            + Utils.ipStr(remote.getAddress().getAddress()) + ":" + remote.getPort();
    }

    @Override
    public String toString() {
        return "Client" + super.toString();
    }
}
