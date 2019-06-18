package vproxy.util;

import vproxy.util.ringbuffer.SimpleRingBuffer;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
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
            return storeBytesFrom((ReadableByteChannel) channel);
        } catch (IOException e) {
            // it's memory operation, should not happen
            throw new RuntimeException(e);
        }
    }

    int storeBytesFrom(ReadableByteChannel channel) throws IOException;

    default int writeTo(ByteArrayChannel channel) {
        try {
            return writeTo((WritableByteChannel) channel);
        } catch (IOException e) {
            // it's memory operation, should not raise error
            throw new RuntimeException(e);
        }
    }

    default int writeTo(WritableByteChannel channel) throws IOException {
        return writeTo(channel, Integer.MAX_VALUE);
    }

    int writeTo(WritableByteChannel channel, int maxBytesToWrite) throws IOException;

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

        ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(new byte[len]);
        int n = writeTo(chnl);
        buffer.storeBytesFrom(chnl);
        return n;
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

    void close();

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
}
