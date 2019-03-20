package net.cassite.vproxy.connection;

import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.Utils;

import java.io.IOException;
import java.net.*;
import java.nio.channels.SocketChannel;

public class ClientConnection extends Connection {
    private static final Inet4Address anyAddr4;
    private static final Inet6Address anyAddr6;

    static {
        try {
            anyAddr4 = (Inet4Address) InetAddress.getByName("0.0.0.0");
            anyAddr6 = (Inet6Address) InetAddress.getByName("[0000:0000:0000:0000:0000:0000:0000:0000]");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

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
        { // check whether need to bind a local addr
            InetAddress localAddr = local.getAddress();
            if (!localAddr.equals(anyAddr4) && !localAddr.equals(anyAddr6)) {
                // also set reuseaddr option
                // otherwise we cannot establish connection between same local addr and different remote addr
                // however it's not necessary if we do not bind
                channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                channel.bind(local);
            }
        }
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

    // generate the id if not specified in constructor
    void regenId() {
        if (local.getPort() != 0) {
            return;
        }
        InetSocketAddress a;
        try {
            a = (InetSocketAddress) channel.getLocalAddress();
        } catch (IOException ignore) {
            return;
        }
        local = a;
        _id = genId();
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
