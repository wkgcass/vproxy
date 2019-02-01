package net.cassite.vproxy.connection;

import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.Utils;

import java.io.IOException;
import java.net.*;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;

public class ClientConnection extends Connection {
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
            return new ClientConnection(Protocol.TCP, channel, remote, inBuffer, outBuffer);
        } catch (IOException e) {
            channel.close(); // close the channel if create ClientConnection failed
            throw e;
        }
    }

    public static ClientConnection createUDP(InetSocketAddress remote, InetSocketAddress local,
                                             RingBuffer inBuffer, RingBuffer outBuffer) throws IOException {
        DatagramChannel channel = DatagramChannel.open(
            (remote.getAddress() instanceof Inet6Address)
                ? StandardProtocolFamily.INET6
                : StandardProtocolFamily.INET
        );
        channel.configureBlocking(false);
        channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        channel.bind(local);
        channel.connect(remote);
        try {
            return new ClientConnection(Protocol.UDP, channel, remote, inBuffer, outBuffer);
        } catch (IOException e) {
            channel.close(); // close the channel if create ClientConnection failed
            throw e;
        }
    }

    private ClientConnection(Protocol protocol, SelectableChannel channel, InetSocketAddress remote,
                             RingBuffer inBuffer, RingBuffer outBuffer) throws IOException {
        super(protocol, channel, remote, inBuffer, outBuffer, true/*it behaves like a connection*/);

        // then let's bind the ET handler
        // it's useful for udp client because it looks like a connection
        if (protocol == Protocol.UDP) {
            this.inBuffer.addHandler(inBufferETHandler);
        }
    }

    @Override
    protected String genId() {
        return (protocol == Protocol.UDP ? "UDP:" : "")
            + (local == null ? "[unbound]" :
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
