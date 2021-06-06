package vproxy.vlibbase;

import vproxy.base.connection.Connection;
import vproxy.base.util.Logger;
import vproxy.base.util.RingBuffer;
import vproxy.base.util.Tuple;
import vproxy.base.util.ringbuffer.SSLUnwrapRingBuffer;
import vproxy.base.util.ringbuffer.SSLUtils;
import vproxy.vclient.GeneralSSLClientOptions;
import vproxy.vclient.impl.AbstractClient;
import vproxy.vfd.IPPort;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.util.Collections;

public class VProxyLibUtils {
    private VProxyLibUtils() {
    }

    public static Tuple<RingBuffer, RingBuffer> buildBuffers(IPPort remote, GeneralSSLClientOptions<?> opts) {
        RingBuffer in;
        RingBuffer out;
        if (opts.sslContext != null) {
            SSLEngine engine = opts.sslContext.createSSLEngine();
            engine.setUseClientMode(true);
            SSLParameters params = new SSLParameters();
            if (opts.host != null) {
                params.setServerNames(Collections.singletonList(new SNIHostName(opts.host)));
            }
            if (opts.alpn != null && opts.alpn.length > 0) {
                params.setApplicationProtocols(opts.alpn);
            }
            engine.setSSLParameters(params);
            SSLUtils.SSLBufferPair pair = SSLUtils.genbuf(engine, RingBuffer.allocate(24576), RingBuffer.allocate(24576), remote);
            in = pair.left;
            out = pair.right;
        } else {
            in = RingBuffer.allocate(1024);
            out = RingBuffer.allocate(1024);
        }
        return new Tuple<>(in, out);
    }

    public static void checkTransfer(AbstractClient client, ConnRef conn) throws IOException {
        if (client.isClosed()) {
            throw new IOException("the client is closed");
        }
        if (!conn.isTransferring()) {
            throw new IllegalArgumentException("the connection " + conn + " is not transferring");
        }
        if (!conn.isValidRef()) {
            throw new IllegalArgumentException("the connection " + conn + " is not valid");
        }
    }

    public static void switchBuffers(Connection raw, GeneralSSLClientOptions<?> opts) throws IOException {
        if (opts == null || opts.sslContext == null) {
            assert Logger.lowLevelDebug("opts not requiring ssl, directly switch");
            return;
        }
        if (raw.getInBuffer() instanceof SSLUnwrapRingBuffer) {
            assert Logger.lowLevelDebug("buffers are already ssl, nothing required to be done");
            return;
        }
        assert Logger.lowLevelDebug("need to switch to ssl buffers");
        var tup = buildBuffers(raw.remote, opts);
        var in = tup.left;
        var out = tup.right;

        raw.UNSAFE_replaceBuffer(in, out, true);
    }
}
