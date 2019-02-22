package net.cassite.vproxy.util.ringbuffer;

import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.Tuple;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
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
                                       SimpleRingBuffer input,
                                       SimpleRingBuffer output,
                                       SelectorEventLoop loop) {
        return genbuf(engine, input, output, loop::runOnLoop);
    }

    public static SSLBufferPair genbuf(SSLEngine engine,
                                       SimpleRingBuffer input,
                                       SimpleRingBuffer output,
                                       Consumer<Runnable> resumer) {
        SSLWrapRingBuffer wrap = new SSLWrapRingBuffer(output, engine, resumer);
        SSLUnwrapRingBuffer unwrap = new SSLUnwrapRingBuffer(input, engine, resumer, wrap);
        return new SSLBufferPair(unwrap, wrap);
    }

    public static SimpleRingBuffer resizeFor(SimpleRingBuffer buf, SSLEngine engine) {
        int size = Math.max(16384, engine.getSession().getPacketBufferSize());
        if (buf != null) {
            // should check whether need to resize
            if (size <= buf.capacity()) {
                return buf; // no need to resize
            }
        }

        // do resize
        // use heap buffer for output
        // it will interact heavily with java code
        SimpleRingBuffer b = RingBuffer.allocate(size);

        // check whether still have data
        if (buf != null && buf.used() != 0) {
            // if still have data, should copy
            try {
                buf.operateOnByteBufferWriteOut(Integer.MAX_VALUE,
                    bbuf -> b.operateOnByteBufferStoreIn(bb -> {
                        bb.put(bbuf);
                        return true;
                    }));
            } catch (IOException e) {
                // should not happen, it's memory operation
                Logger.shouldNotHappen("copy data failed");
            }
        }

        return b;
    }
}
