package vproxybase.util.direct;

import vproxybase.GlobalInspection;
import vproxybase.util.ByteBufferEx;

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
        if (cleaned) {
            return;
        }
        cleaned = true;
        DirectMemoryUtils.free(buffer);
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
