package vproxy.connection;

import vfd.FDProvider;
import vfd.SocketFD;
import vproxy.util.RingBuffer;
import vproxy.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;

public class ConnectableConnection extends Connection {
    Connector connector; // maybe null, only for recording purpose, will not be used by the connection lib

    public Connector getConnector() {
        return connector;
    }

    public static ConnectableConnection create(InetSocketAddress remote,
                                               ConnectionOpts opts,
                                               RingBuffer inBuffer, RingBuffer outBuffer) throws IOException {
        SocketFD channel = FDProvider.get().openSocketFD();
        try {
            channel.configureBlocking(false);
            channel.connect(remote);
            return new ConnectableConnection(channel, remote, opts, inBuffer, outBuffer);
        } catch (IOException e) {
            channel.close();
            throw e;
        }
    }

    private ConnectableConnection(SocketFD channel, InetSocketAddress remote,
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
        return "Connectable" + super.toString();
    }
}
