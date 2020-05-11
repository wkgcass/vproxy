package vproxy.util.ringbuffer;

import vfd.NetworkFD;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.Tuple;
import vproxy.util.Utils;
import vproxy.util.ringbuffer.ssl.SSL;

import javax.net.ssl.*;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.function.Consumer;

public class SSLUtils {
    private static SSLContext defaultClientSSLContext;

    private SSLUtils() {
    }

    public static class SSLBufferPair extends Tuple<SSLUnwrapRingBuffer, SSLWrapRingBuffer> {
        public SSLBufferPair(SSLUnwrapRingBuffer left, SSLWrapRingBuffer right) {
            super(left, right);
        }
    }

    // use event loop as resumer
    // use remote address to mirror
    public static SSLBufferPair genbuf(SSLEngine engine,
                                       ByteBufferRingBuffer input,
                                       ByteBufferRingBuffer output,
                                       SelectorEventLoop loop,
                                       InetSocketAddress remote) {
        return genbuf(engine, input, output, loop::nextTick, remote);
    }

    // use callback function as resumer
    // use remtoe address to mirror
    public static SSLBufferPair genbuf(SSLEngine engine,
                                       ByteBufferRingBuffer input,
                                       ByteBufferRingBuffer output,
                                       Consumer<Runnable> resumer,
                                       InetSocketAddress remote) {
        SSLWrapRingBuffer wrap = new SSLWrapRingBuffer(output, engine, remote);
        SSLUnwrapRingBuffer unwrap = new SSLUnwrapRingBuffer(input, engine, resumer, wrap, remote);
        return new SSLBufferPair(unwrap, wrap);
    }

    // use callback function as resumer
    // no mirror address info
    // DO NOT USE THIS EXCEPT FOR TESTING
    public static SSLBufferPair genbuf(SSLEngine engine,
                                       ByteBufferRingBuffer input,
                                       ByteBufferRingBuffer output,
                                       Consumer<Runnable> resumer) {
        SSLWrapRingBuffer wrap = new SSLWrapRingBuffer(output, engine, Utils::bindAnyAddress, Utils::bindAnyAddress);
        SSLUnwrapRingBuffer unwrap = new SSLUnwrapRingBuffer(input, engine, resumer, wrap, Utils::bindAnyAddress, Utils::bindAnyAddress);
        return new SSLBufferPair(unwrap, wrap);
    }

    // use callback function as resumer
    // use fd info to mirror
    public static SSLBufferPair genbuf(SSLEngine engine,
                                       ByteBufferRingBuffer input,
                                       ByteBufferRingBuffer output,
                                       Consumer<Runnable> resumer,
                                       NetworkFD fd) {
        SSLWrapRingBuffer wrap = new SSLWrapRingBuffer(output, engine, fd);
        SSLUnwrapRingBuffer unwrap = new SSLUnwrapRingBuffer(input, engine, resumer, wrap, fd);
        return new SSLBufferPair(unwrap, wrap);
    }

    // server ssl info
    // use callback function as resumer
    // use fd info to mirror
    public static SSLBufferPair genbufForServer(SSL ssl,
                                                ByteBufferRingBuffer input,
                                                ByteBufferRingBuffer output,
                                                Consumer<Runnable> resumer,
                                                NetworkFD fd) {
        SSLWrapRingBuffer wrap = new SSLWrapRingBuffer(output, fd);
        SSLUnwrapRingBuffer unwrap = new SSLUnwrapRingBuffer(input, ssl, resumer, wrap, fd);
        return new SSLBufferPair(unwrap, wrap);
    }

    // do not run resumer
    // use remote address to mirror
    public static SSLBufferPair genbuf(SSLEngine engine,
                                       ByteBufferRingBuffer input,
                                       ByteBufferRingBuffer output,
                                       InetSocketAddress remote) {
        return genbuf(engine, input, output, (Consumer<Runnable>) null, remote);
    }

    // do not run resumer
    // use fd info to mirror
    public static SSLBufferPair genbuf(SSLEngine engine,
                                       ByteBufferRingBuffer input,
                                       ByteBufferRingBuffer output,
                                       NetworkFD fd) {
        return genbuf(engine, input, output, (Consumer<Runnable>) null, fd);
    }

    // do not run resumer
    // use fd into to mirror
    public static SSLBufferPair genbufForServer(SSL ssl,
                                                ByteBufferRingBuffer input,
                                                ByteBufferRingBuffer output,
                                                NetworkFD fd) {
        return genbufForServer(ssl, input, output, null, fd);
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
