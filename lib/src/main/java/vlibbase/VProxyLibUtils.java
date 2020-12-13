package vlibbase;

import vclient.GeneralClientOptions;
import vclient.impl.AbstractClient;
import vfd.IPPort;
import vproxybase.util.RingBuffer;
import vproxybase.util.Tuple;
import vproxybase.util.ringbuffer.SSLUtils;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.util.Collections;

public class VProxyLibUtils {
    private VProxyLibUtils() {
    }

    public static Tuple<RingBuffer, RingBuffer> buildBuffers(IPPort remote, GeneralClientOptions<?> opts) {
        RingBuffer in;
        RingBuffer out;
        if (opts.sslContext != null) {
            SSLEngine engine = opts.sslContext.createSSLEngine();
            engine.setUseClientMode(true);
            SSLParameters params = new SSLParameters();
            if (opts.host != null) {
                params.setServerNames(Collections.singletonList(new SNIHostName(opts.host)));
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
}
