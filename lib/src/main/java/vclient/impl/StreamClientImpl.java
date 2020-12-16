package vclient.impl;

import vlibbase.Conn;
import vclient.StreamClient;
import vfd.IPPort;
import vlibbase.ConnRef;
import vlibbase.VProxyLibUtils;
import vlibbase.impl.ConnImpl;
import vproxybase.connection.ConnectableConnection;
import vproxybase.connection.ConnectionOpts;
import vproxybase.util.RingBuffer;

import java.io.IOException;
import java.util.function.BiConsumer;

public class StreamClientImpl extends AbstractClient implements StreamClient {
    private final IPPort remote;
    private final Options opts;

    public StreamClientImpl(IPPort remote, Options opts) {
        super(opts);
        this.remote = remote;
        this.opts = new Options(opts);
    }

    @Override
    public void connect(BiConsumer<IOException, Conn> connectionCallback) {
        getLoop();

        RingBuffer in;
        RingBuffer out;
        var bufTup = VProxyLibUtils.buildBuffers(remote, opts);
        in = bufTup.left;
        out = bufTup.right;

        ConnectableConnection conn;
        try {
            conn = ConnectableConnection.create(
                remote,
                new ConnectionOpts().setTimeout(opts.timeout),
                in, out);
        } catch (IOException e) {
            connectionCallback.accept(e, null);
            return;
        }

        try {
            new ConnImpl(getLoop(), conn, connectionCallback);
        } catch (IOException e) {
            conn.close();
            connectionCallback.accept(e, null);
            //noinspection UnnecessaryReturnStatement
            return;
        }
    }

    @Override
    public Conn receiveTransferredConnection0(ConnRef conn) throws IOException {
        VProxyLibUtils.checkTransfer(this, conn);
        getLoop();

        var raw = conn.raw();
        raw.setTimeout(opts.timeout);
        VProxyLibUtils.switchBuffers(raw, opts);
        return new ConnImpl(getLoop(), raw, null);
    }
}
