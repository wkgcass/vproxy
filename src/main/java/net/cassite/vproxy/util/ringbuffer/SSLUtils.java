package net.cassite.vproxy.util.ringbuffer;

import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.util.Tuple;

import javax.net.ssl.SSLEngine;
import java.util.function.Consumer;

public class SSLUtils {
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
                                       int inputCap, int outputCap,
                                       SelectorEventLoop loop) {
        return genbuf(engine, input, output, inputCap, outputCap, loop::nextTick);
    }

    public static SSLBufferPair genbuf(SSLEngine engine,
                                       ByteBufferRingBuffer input,
                                       ByteBufferRingBuffer output,
                                       int inputCap, int outputCap,
                                       Consumer<Runnable> resumer) {
        SSLWrapRingBuffer wrap = new SSLWrapRingBuffer(output, outputCap, engine);
        SSLUnwrapRingBuffer unwrap = new SSLUnwrapRingBuffer(input, inputCap, engine, resumer, wrap);
        return new SSLBufferPair(unwrap, wrap);
    }
}
