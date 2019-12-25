package vproxy.util.ringbuffer;

import vproxy.connection.Connection;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.ByteArray;
import vproxy.util.RingBuffer;
import vproxy.util.Tuple;

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

    public static SSLBufferPair genbuf(SSLEngine engine,
                                       ByteBufferRingBuffer input,
                                       ByteBufferRingBuffer output) {
        return genbuf(engine, input, output, (Consumer<Runnable>) null);
    }

    public static SSLEngine getEngineFrom(Connection connection) {
        RingBuffer buf = connection.getInBuffer();
        if (!(buf instanceof SSLUnwrapRingBuffer)) {
            throw new IllegalArgumentException();
        }
        SSLUnwrapRingBuffer sslRingBuffer = (SSLUnwrapRingBuffer) buf;
        return sslRingBuffer.engine;
    }

    public static ByteArray getPlainBufferBytes(SSLUnwrapRingBuffer buf) {
        RingBuffer buffer = buf.plainBufferForApp;
        return ByteArray.from(buffer.getBytes());
    }
}
