package net.cassite.vproxy.connection;

import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;

public class ClientConnection extends Connection {
    Connector connector; // maybe null, only for recording purpose, will not be used by the connection lib

    public Connector getConnector() {
        return connector;
    }

    public static ClientConnection create(InetSocketAddress remote,
                                          ConnectionOpts opts,
                                          RingBuffer inBuffer, RingBuffer outBuffer) throws IOException {
        SocketChannel channel = SocketChannel.open();
        try {
            channel.configureBlocking(false);
            // we want to simply reset the connection when closing
            channel.setOption(StandardSocketOptions.SO_LINGER, 0);
            channel.connect(remote);
            return new ClientConnection(channel, remote, opts, inBuffer, outBuffer);
        } catch (IOException e) {
            channel.close();
            throw e;
        }
    }

    private ClientConnection(SocketChannel channel, InetSocketAddress remote,
                             ConnectionOpts opts,
                             RingBuffer inBuffer, RingBuffer outBuffer) {
        super(channel, remote, null, opts, inBuffer, outBuffer);
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
