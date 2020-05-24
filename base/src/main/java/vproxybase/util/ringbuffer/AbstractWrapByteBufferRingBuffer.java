package vproxybase.util.ringbuffer;

import java.io.IOException;

public abstract class AbstractWrapByteBufferRingBuffer extends AbstractWrapRingBuffer implements ByteBufferRingBuffer {
    public AbstractWrapByteBufferRingBuffer(ByteBufferRingBuffer plainBytesBuffer) {
        super(plainBytesBuffer);
    }

    @Override
    public int operateOnByteBufferWriteOut(int maxBytesToWrite, WriteOutOp op) throws IOException {
        return getPlainBufferForApp().operateOnByteBufferWriteOut(maxBytesToWrite, op);
    }

    @Override
    public int operateOnByteBufferStoreIn(StoreInOp op) throws IOException {
        return getPlainBufferForApp().operateOnByteBufferStoreIn(op);
    }

    @Override
    public boolean canDefragment() {
        return getPlainBufferForApp().canDefragment();
    }

    @Override
    public void defragment() {
        getPlainBufferForApp().defragment();
    }
}
