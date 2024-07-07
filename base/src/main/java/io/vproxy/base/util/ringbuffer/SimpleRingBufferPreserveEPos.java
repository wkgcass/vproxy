package io.vproxy.base.util.ringbuffer;

import io.vproxy.base.util.ByteBufferEx;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

public class SimpleRingBufferPreserveEPos extends SimpleRingBuffer {
    protected SimpleRingBufferPreserveEPos(boolean isDirect, ByteBufferEx buffer, int sPos, int ePos) {
        super(isDirect, buffer, sPos, ePos);
    }

    public static SimpleRingBufferPreserveEPos wrap(ByteBuffer b) {
        return new SimpleRingBufferPreserveEPos(false, new ByteBufferEx(b), b.position(), b.limit());
    }

    public static SimpleRingBufferPreserveEPos wrap(MemorySegment memSeg) {
        var buf = memSeg.asByteBuffer();
        buf.limit(0).position(0);
        return wrap(buf);
    }

    @Override
    protected void resetCursors() {
        sPos = getEPos();
        ePosIsAfterSPos = true;
    }
}
