package vproxybase.connection;

import vfd.FDProvider;
import vfd.IPPort;
import vfd.SocketFD;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.selector.wrap.udp.UDPBasedFDs;
import vproxybase.selector.wrap.udp.UDPFDs;
import vproxybase.util.LogType;
import vproxybase.util.Logger;
import vproxybase.util.RingBuffer;

import java.io.IOException;
import java.net.StandardSocketOptions;

public class ConnectableConnection extends Connection {
    Connector connector; // maybe null, only for recording purpose, will not be used by the connection lib

    public Connector getConnector() {
        return connector;
    }

    private static ConnectableConnection create(SocketFD channel, IPPort remote, ConnectionOpts opts,
                                                RingBuffer inBuffer, RingBuffer outBuffer) throws IOException {
        try {
            channel.configureBlocking(false);
            channel.connect(remote);
            return new ConnectableConnection(channel, remote, opts, inBuffer, outBuffer);
        } catch (IOException e) {
            channel.close();
            throw e;
        }
    }

    public static ConnectableConnection create(IPPort remote,
                                               ConnectionOpts opts,
                                               RingBuffer inBuffer, RingBuffer outBuffer) throws IOException {
        SocketFD channel = FDProvider.get().openSocketFD();
        return create(channel, remote, opts, inBuffer, outBuffer);
    }

    public static ConnectableConnection createUDP(IPPort remote,
                                                  ConnectionOpts opts,
                                                  RingBuffer inBuffer, RingBuffer outBuffer) throws IOException {
        return createUDP(remote, opts, inBuffer, outBuffer, null, UDPFDs.get());
    }

    public static ConnectableConnection createUDP(IPPort remote,
                                                  ConnectionOpts opts,
                                                  RingBuffer inBuffer, RingBuffer outBuffer,
                                                  SelectorEventLoop loop,
                                                  UDPBasedFDs fds) throws IOException {
        SocketFD channel = fds.openSocketFD(loop);
        int buflen = 1024 * 1024; // make 1mBytes receiving buffer
        try {
            channel.setOption(StandardSocketOptions.SO_RCVBUF, buflen);
        } catch (IOException e) {
            Logger.warn(LogType.SOCKET_ERROR, "setting SO_RCVBUF to " + buflen + " for " + channel + " failed: " + e);
        }
        return create(channel, remote, opts, inBuffer, outBuffer);
    }

    private ConnectableConnection(SocketFD channel, IPPort remote,
                                  ConnectionOpts opts,
                                  RingBuffer inBuffer, RingBuffer outBuffer) {
        super(channel, remote, null, opts, inBuffer, outBuffer);
    }

    // generate the id if not specified in constructor
    void regenId() {
        if (local != null) {
            return;
        }
        IPPort a;
        try {
            a = channel.getLocalAddress();
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
                local.formatToIPPortString()
            ))
            + "/"
            + remote.formatToIPPortString();
    }

    @Override
    public String toString() {
        return "Connectable" + super.toString();
    }
}
