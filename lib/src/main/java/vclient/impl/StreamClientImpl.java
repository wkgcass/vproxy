package vclient.impl;

import vlibbase.Conn;
import vclient.StreamClient;
import vfd.IPPort;
import vlibbase.ConnRef;
import vlibbase.impl.ConnImpl;
import vproxybase.connection.ConnectableConnection;
import vproxybase.connection.ConnectionOpts;
import vproxybase.util.RingBuffer;
import vproxybase.util.ringbuffer.SSLUtils;

import javax.net.ssl.SSLEngine;
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
        if (opts.sslContext != null) {
            SSLEngine engine = opts.sslContext.createSSLEngine();
            engine.setUseClientMode(true);
            SSLUtils.SSLBufferPair pair = SSLUtils.genbuf(engine, RingBuffer.allocate(24576), RingBuffer.allocate(24576), remote);
            in = pair.left;
            out = pair.right;
        } else {
            in = RingBuffer.allocate(1024);
            out = RingBuffer.allocate(1024);
        }

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
        if (isClosed()) {
            throw new IOException("the client is closed");
        }
        if (!conn.isTransferring()) {
            throw new IllegalArgumentException("the connection " + conn + " is not transferring");
        }
        if (!conn.isValidRef()) {
            throw new IllegalArgumentException("the connection " + conn + " is not valid");
        }
        getLoop();

        return new ConnImpl(getLoop(), conn.raw(), null);
    }
}
