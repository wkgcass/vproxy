package io.vproxy.base.util.ringbuffer;

import io.vproxy.base.util.coll.Tuple;
import io.vproxy.base.util.ringbuffer.ssl.SSL;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.NetworkFD;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

public class SSLUtils {
    private static SSLContext defaultClientSSLContext;

    private SSLUtils() {
    }

    public static class SSLBufferPair extends Tuple<SSLUnwrapRingBuffer, SSLWrapRingBuffer> {
        public SSLBufferPair(SSLUnwrapRingBuffer left, SSLWrapRingBuffer right) {
            super(left, right);
        }
    }

    // use remote address to mirror
    public static SSLBufferPair genbuf(SSLEngine engine,
                                       ByteBufferRingBuffer input,
                                       ByteBufferRingBuffer output,
                                       IPPort remote) {
        SSLWrapRingBuffer wrap = new SSLWrapRingBuffer(output, engine, remote);
        SSLUnwrapRingBuffer unwrap = new SSLUnwrapRingBuffer(input, engine, wrap, remote);
        return new SSLBufferPair(unwrap, wrap);
    }

    // no mirror address info
    // DO NOT USE THIS EXCEPT FOR TESTING
    public static SSLBufferPair genbuf(SSLEngine engine,
                                       ByteBufferRingBuffer input,
                                       ByteBufferRingBuffer output) {
        SSLWrapRingBuffer wrap = new SSLWrapRingBuffer(output, engine, IPPort::bindAnyAddress, IPPort::bindAnyAddress);
        SSLUnwrapRingBuffer unwrap = new SSLUnwrapRingBuffer(input, engine, wrap, IPPort::bindAnyAddress, IPPort::bindAnyAddress);
        return new SSLBufferPair(unwrap, wrap);
    }

    // use fd info to mirror
    public static SSLBufferPair genbuf(SSLEngine engine,
                                       ByteBufferRingBuffer input,
                                       ByteBufferRingBuffer output,
                                       NetworkFD<IPPort> fd) {
        SSLWrapRingBuffer wrap = new SSLWrapRingBuffer(output, engine, fd);
        SSLUnwrapRingBuffer unwrap = new SSLUnwrapRingBuffer(input, engine, wrap, fd);
        return new SSLBufferPair(unwrap, wrap);
    }

    // server ssl info
    // use fd info to mirror
    public static SSLBufferPair genbufForServer(SSL ssl,
                                                ByteBufferRingBuffer input,
                                                ByteBufferRingBuffer output,
                                                NetworkFD<IPPort> fd) {
        SSLWrapRingBuffer wrap = new SSLWrapRingBuffer(output, fd);
        SSLUnwrapRingBuffer unwrap = new SSLUnwrapRingBuffer(input, ssl, wrap, fd);
        return new SSLBufferPair(unwrap, wrap);
    }

    public static SSLContext getDefaultClientSSLContext() {
        if (defaultClientSSLContext != null) {
            return defaultClientSSLContext;
        }

        KeyManager[] kms = null;
        TrustManager[] tms = null;

        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
            //noinspection ConstantConditions
            sslContext.init(kms, tms, null);
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        defaultClientSSLContext = sslContext;
        return sslContext;
    }
}
