package io.vproxy.base.util;

import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.base.util.ringbuffer.SimpleRingBuffer;
import io.vproxy.vfd.ReadableByteStream;
import io.vproxy.vfd.WritableByteStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;

public interface RingBuffer {
    SimpleRingBuffer EMPTY_BUFFER = allocate(0);

    static SimpleRingBuffer allocateDirect(int cap) {
        return SimpleRingBuffer.allocateDirect(cap);
    }

    static SimpleRingBuffer allocate(int cap) {
        return SimpleRingBuffer.allocate(cap);
    }

    default int storeBytesFrom(ByteArrayChannel channel) {
        try {
            return storeBytesFrom((ReadableByteStream) channel);
        } catch (IOException e) {
            // it's memory operation, should not happen
            throw new RuntimeException(e);
        }
    }

    default int storeBytesFrom(ByteArray array) {
        return storeBytesFrom(array.toFullChannel());
    }

    int storeBytesFrom(ReadableByteStream channel) throws IOException;

    default int storeBytesFrom(ByteBuffer buf) {
        var oldLimit = buf.limit();

        var chnl = ByteArrayChannel.fromFull(buf);
        var ret = storeBytesFrom(chnl);

        buf.limit(oldLimit);
        return ret;
    }

    default int writeTo(ByteArrayChannel channel) {
        try {
            return writeTo((WritableByteStream) channel);
        } catch (IOException e) {
            // it's memory operation, should not raise error
            throw new RuntimeException(e);
        }
    }

    default int writeTo(ByteArray array) {
        return writeTo(array.toEmptyChannel());
    }

    default int writeTo(WritableByteStream channel) throws IOException {
        return writeTo(channel, Integer.MAX_VALUE);
    }

    int writeTo(WritableByteStream channel, int maxBytesToWrite) throws IOException;

    default int writeTo(RingBuffer buffer) {
        return writeTo(buffer, Integer.MAX_VALUE);
    }

    default int writeTo(RingBuffer buffer, int maxBytesToWrite) {
        // NOTE: the default implementation of this method is general but with low efficiency
        // ByteBufferRingBuffer has a fast implementation

        if (maxBytesToWrite < 0) {
            throw new IllegalArgumentException("input parameter maxBytesToWrite = " + maxBytesToWrite + " < 0");
        }

        int bufferFreeSpace = buffer.free();
        int len = Math.min(maxBytesToWrite, bufferFreeSpace);
        if (len == 0) {
            return 0; // nothing to write for now
        }

        ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(Utils.allocateByteArray(len));
        int n = writeTo(chnl);
        buffer.storeBytesFrom(chnl);
        return n;
    }

    default int writeTo(ByteBuffer buf) {
        var oldLimit = buf.limit();

        var chnl = ByteArrayChannel.fromEmpty(buf);
        var ret = writeTo(chnl);

        buf.limit(oldLimit);
        return ret;
    }

    int free();

    int used();

    int capacity();

    default byte[] getBytes() {
        throw new UnsupportedOperationException();
    }

    void addHandler(RingBufferETHandler h);

    void removeHandler(RingBufferETHandler h);

    Set<RingBufferETHandler> getHandlers();

    void clean();

    void clear();

    class RejectSwitchException extends Exception {
        public RejectSwitchException(String msg) {
            super(msg);
        }
    }

    default RingBuffer switchBuffer(RingBuffer buf) throws RejectSwitchException {
        // default: simply use the new buffer when need to switch
        return buf;
    }

    default boolean isParentOf(RingBuffer buf) {
        // default: no inside structures
        return false;
    }

    static boolean haveRelationBetween(RingBuffer a, RingBuffer b) {
        return a.isParentOf(b) || b.isParentOf(a);
    }
}
