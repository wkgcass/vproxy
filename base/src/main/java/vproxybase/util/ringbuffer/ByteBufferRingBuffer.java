package vproxybase.util.ringbuffer;

import vproxybase.util.RingBuffer;

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

    @Override
    default int writeTo(RingBuffer buffer, int maxBytesToWrite) {
        if (!(buffer instanceof ByteBufferRingBuffer)) {
            // use the default implementation when the target buffer is not byteBufferRingBuffer
            return RingBuffer.super.writeTo(buffer, maxBytesToWrite);
        }
        // otherwise, use a faster implementation

        ByteBufferRingBuffer targetBuffer = (ByteBufferRingBuffer) buffer;
        try {
            return operateOnByteBufferWriteOut(maxBytesToWrite, srcBuf ->
                targetBuffer.operateOnByteBufferStoreIn(tarBuf -> {
                    // check and set srcBuf limit
                    final int backupSrcBufLim = srcBuf.limit();

                    int srcLen = backupSrcBufLim - srcBuf.position();
                    int tarLen = tarBuf.limit() - tarBuf.position();

                    if (srcLen > tarLen) {
                        // reduce the limit to make it suit target buffer
                        srcBuf.limit(srcBuf.position() + tarLen);
                    }

                    // store into target
                    tarBuf.put(srcBuf);

                    // restore the limit
                    srcBuf.limit(backupSrcBufLim);

                    // memory operation, always success
                    return true;
                }));
        } catch (IOException e) {
            // will not happen, it's memory operation
            throw new RuntimeException(e);
        }
    }
}
