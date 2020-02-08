package vproxy.util.ringbuffer;

import vproxy.selector.SelectorEventLoop;
import vproxy.util.Tuple;
import vproxy.util.ringbuffer.ssl.SSL;

import javax.net.ssl.*;
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

    public static SSLBufferPair genbuf(SSLEngine engine,
                                       ByteBufferRingBuffer input,
                                       ByteBufferRingBuffer output,
                                       SelectorEventLoop loop) {
        return genbuf(engine, input, output, loop::nextTick);
    }

    public static SSLBufferPair genbuf(SSLEngine engine,
                                       ByteBufferRingBuffer input,
                                       ByteBufferRingBuffer output,
                                       Consumer<Runnable> resumer) {
        SSLWrapRingBuffer wrap = new SSLWrapRingBuffer(output, engine);
        SSLUnwrapRingBuffer unwrap = new SSLUnwrapRingBuffer(input, engine, resumer, wrap);
        return new SSLBufferPair(unwrap, wrap);
    }

    public static SSLBufferPair genbufForServer(SSL ssl,
                                                ByteBufferRingBuffer input,
                                                ByteBufferRingBuffer output,
                                                Consumer<Runnable> resumer) {
        SSLWrapRingBuffer wrap = new SSLWrapRingBuffer(output);
        SSLUnwrapRingBuffer unwrap = new SSLUnwrapRingBuffer(input, ssl, resumer, wrap);
        return new SSLBufferPair(unwrap, wrap);
    }

    public static SSLBufferPair genbuf(SSLEngine engine,
                                       ByteBufferRingBuffer input,
                                       ByteBufferRingBuffer output) {
        return genbuf(engine, input, output, (Consumer<Runnable>) null);
    }

    public static SSLBufferPair genbufForServer(SSL ssl,
                                                ByteBufferRingBuffer input,
                                                ByteBufferRingBuffer output) {
        return genbufForServer(ssl, input, output, null);
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
