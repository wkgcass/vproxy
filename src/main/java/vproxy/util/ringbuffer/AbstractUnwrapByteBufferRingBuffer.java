package vproxy.util.ringbuffer;

import java.io.IOException;

public abstract class AbstractUnwrapByteBufferRingBuffer extends AbstractUnwrapRingBuffer implements ByteBufferRingBuffer {
    public AbstractUnwrapByteBufferRingBuffer(ByteBufferRingBuffer plainBufferForApp) {
        super(plainBufferForApp);
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
