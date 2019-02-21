package net.cassite.vproxy.util.ringbuffer;

import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.RingBuffer;

import javax.net.ssl.SSLEngine;
import java.io.IOException;

public class SSLUtils {
    private SSLUtils() {
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
