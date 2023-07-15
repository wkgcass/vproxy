package io.vproxy.base.util.direct;

import io.vproxy.base.GlobalInspection;
import io.vproxy.base.util.ByteBufferEx;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

public class DirectByteBuffer extends ByteBufferEx {
    private boolean cleaned = false;
    protected final MemorySegment seg;

    DirectByteBuffer(ByteBuffer buffer) {
        super(buffer);
        this.seg = MemorySegment.ofBuffer(buffer);
    }

    @Override
    public ByteBuffer realBuffer() {
        if (cleaned) {
            return null;
        }
        return buffer;
    }

    public MemorySegment getMemorySegment() {
        return seg;
    }

    @Override
    public void clean() {
        clean(true);
    }

    public void clean(boolean tryCache) {
        if (cleaned) {
            return;
        }
        cleaned = DirectMemoryUtils.free(this, tryCache);
    }

    @SuppressWarnings({"removal"})
    @Override
    protected void finalize() throws Throwable {
        try {
            if (cleaned) {
                return;
            }
            GlobalInspection.getInstance().directBufferFinalize(cap);
        } finally {
            super.finalize();
        }
    }
}
