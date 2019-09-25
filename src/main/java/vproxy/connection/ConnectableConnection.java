package vproxy.connection;

import vproxy.util.RingBuffer;
import vproxy.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class ConnectableConnection extends Connection {
    Connector connector; // maybe null, only for recording purpose, will not be used by the connection lib

    public Connector getConnector() {
        return connector;
    }

    public static ConnectableConnection create(InetSocketAddress remote,
                                               ConnectionOpts opts,
                                               RingBuffer inBuffer, RingBuffer outBuffer) throws IOException {
        SocketChannel channel = SocketChannel.open();
        try {
            channel.configureBlocking(false);
            channel.connect(remote);
            return new ConnectableConnection(channel, remote, opts, inBuffer, outBuffer);
        } catch (IOException e) {
            channel.close();
            throw e;
        }
    }

    private ConnectableConnection(SocketChannel channel, InetSocketAddress remote,
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
