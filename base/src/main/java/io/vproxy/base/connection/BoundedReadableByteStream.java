package io.vproxy.base.connection;

import io.vproxy.vfd.ReadableByteStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Synchronous, loop-owned transport quota; does not retain an FD between callbacks. */
final class BoundedReadableByteStream implements ReadableByteStream {
    private ReadableByteStream source;
    private int remaining;

    void reset(ReadableByteStream source, int bytes) {
        this.source = source;
        remaining = bytes;
    }

    boolean isInUse() {
        return source != null;
    }

    void release() {
        source = null;
        remaining = 0;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (remaining == 0 || !dst.hasRemaining()) return 0;
        int position = dst.position();
        int originalLimit = dst.limit();
        int limited = position + Math.min(dst.remaining(), remaining);
        dst.limit(limited);
        try {
            int result = source.read(dst);
            // Keep the ring's original "read only changes position" contract.
            if (dst.limit() != limited || dst.position() < position || dst.position() > limited) {
                throw new IllegalStateException("should only read in");
            }
            return result;
        } finally {
            remaining -= Math.min(remaining, Math.max(0, dst.position() - position));
            dst.limit(originalLimit);
        }
    }
}
