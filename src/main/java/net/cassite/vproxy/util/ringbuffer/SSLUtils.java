package net.cassite.vproxy.util.ringbuffer;

import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.util.Tuple;

import javax.net.ssl.SSLEngine;
import java.util.function.Consumer;

public class SSLUtils {
    private SSLUtils() {
    }

    /*
     * according to rfc5246 and rfc8449
     *
     * rfc5246: 6.2.3.  Record Payload Protection
     *
     * rfc8449:
     * TLS versions 1.2 [RFC5246] and
     * earlier permit senders to generate records 16384 octets in size, plus
     * any expansion from compression and protection up to 2048 octets
     * (though typically this expansion is only 16 octets).  TLS 1.3 reduces
     * the allowance for expansion to 256 octets.
     */
    public static final int CIPHER_TEXT_SIZE = 16384 + 2048 + (5 /*1 for type, 2 for version, 2 for length*/);

    /**
     * java SSL_ENGINE do not support tls compression by default,
     * so the plain text size would not be larger than the cipher text size.
     */
    public static final int PLAIN_TEXT_SIZE = CIPHER_TEXT_SIZE;

    public static class SSLBufferPair extends Tuple<SSLUnwrapRingBuffer, SSLWrapRingBuffer> {
        public SSLBufferPair(SSLUnwrapRingBuffer left, SSLWrapRingBuffer right) {
            super(left, right);
        }
    }

    public static SSLBufferPair genbuf(SSLEngine engine,
                                       ByteBufferRingBuffer input,
                                       ByteBufferRingBuffer output,
                                       int inputCap, int outputCap,
                                       SelectorEventLoop loop) {
        return genbuf(engine, input, output, inputCap, outputCap, loop::nextTick);
    }

    public static SSLBufferPair genbuf(SSLEngine engine,
                                       ByteBufferRingBuffer input,
                                       ByteBufferRingBuffer output,
                                       int inputCap, int outputCap,
                                       Consumer<Runnable> resumer) {
        if (inputCap < CIPHER_TEXT_SIZE || outputCap < CIPHER_TEXT_SIZE) {
            throw new IllegalArgumentException("inputCap or outputCap < CIPHER_TEXT_SIZE(" + CIPHER_TEXT_SIZE + "), " +
                "we do not support buffer scaling for now");
        }
        if (input.capacity() < PLAIN_TEXT_SIZE) {
            throw new IllegalArgumentException("app input buffer < PLAIN_TEXT_SIZE(" + PLAIN_TEXT_SIZE + "), " +
                "we do not support buffer scaling for now");
        }

        SSLWrapRingBuffer wrap = new SSLWrapRingBuffer(output, outputCap, engine);
        SSLUnwrapRingBuffer unwrap = new SSLUnwrapRingBuffer(input, inputCap, engine, resumer, wrap);
        return new SSLBufferPair(unwrap, wrap);
    }

    public static SSLBufferPair genbuf(SSLEngine engine,
                                       ByteBufferRingBuffer input,
                                       ByteBufferRingBuffer output,
                                       int inputCap, int outputCap) {
        return genbuf(engine, input, output, inputCap, outputCap, (Consumer<Runnable>) null);
    }
}
