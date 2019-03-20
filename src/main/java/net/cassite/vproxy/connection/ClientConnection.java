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

    public static ClientConnection create(InetSocketAddress remote, RingBuffer inBuffer, RingBuffer outBuffer) throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.connect(remote);
        try {
            return new ClientConnection(channel, remote, inBuffer, outBuffer);
        } catch (IOException e) {
            channel.close(); // close the channel if create ClientConnection failed
            throw e;
        }
    }

    private ClientConnection(SocketChannel channel, InetSocketAddress remote,
                             RingBuffer inBuffer, RingBuffer outBuffer) throws IOException {
        super(channel, remote, null, inBuffer, outBuffer);
    }

    // generate the id if not specified in constructor
    void regenId() {
        if (local != null) {
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
