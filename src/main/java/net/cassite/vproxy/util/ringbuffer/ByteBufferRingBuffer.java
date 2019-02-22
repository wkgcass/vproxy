package net.cassite.vproxy.util.ringbuffer;

import net.cassite.vproxy.util.RingBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface ByteBufferRingBuffer extends RingBuffer {
    interface WriteOutOp {
        void accept(ByteBuffer buffer) throws IOException;
    }

    int operateOnByteBufferWriteOut(int maxBytesToWrite, WriteOutOp op) throws IOException;

    interface StoreInOp {
        boolean test(ByteBuffer buffer) throws IOException;
    }

    int operateOnByteBufferStoreIn(StoreInOp op) throws IOException;

    boolean canDefragment();

    void defragment();
}
