package io.vproxy.base.util.direct;

import io.vproxy.base.GlobalInspection;
import io.vproxy.base.util.ByteBufferEx;

import java.nio.ByteBuffer;

public class DirectByteBuffer extends ByteBufferEx {
    private boolean cleaned = false;

    DirectByteBuffer(ByteBuffer buffer) {
        super(buffer);
    }

    @Override
    public ByteBuffer realBuffer() {
        if (cleaned) {
            return null;
        }
        return buffer;
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

    @SuppressWarnings("deprecation")
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
