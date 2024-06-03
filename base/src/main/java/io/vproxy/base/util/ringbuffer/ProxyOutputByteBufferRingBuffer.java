package io.vproxy.base.util.ringbuffer;

import io.vproxy.base.util.Logger;
import io.vproxy.base.util.RingBuffer;

import java.io.IOException;

public class ProxyOutputByteBufferRingBuffer extends ProxyOutputRingBuffer implements ByteBufferRingBuffer, RingBuffer {
    protected ProxyOutputByteBufferRingBuffer(SimpleRingBuffer defaultBuffer) {
        super(defaultBuffer);
    }

    public static ProxyOutputByteBufferRingBuffer allocateDirect(int cap) {
        return new ProxyOutputByteBufferRingBuffer(SimpleRingBuffer.allocateDirect(cap));
    }

    @Override
    public void proxy(RingBuffer proxyBuffer, int proxyLen, ProxyDoneCallback cb) {
        if (!(proxyBuffer instanceof ByteBufferRingBuffer)) {
            throw new IllegalArgumentException(proxyBuffer + " is not ByteBufferRingBuffer");
        }
        super.proxy(proxyBuffer, proxyLen, cb);
    }

    @Override
    public int operateOnByteBufferWriteOut(int maxBytesToWrite, WriteOutOp op) throws IOException {
        if (proxyHandle != null && proxyHandle.enabled) {
            int toWrite = Math.min(maxBytesToWrite, proxyHandle.len);
            int wrote = ((ByteBufferRingBuffer) proxyHandle.rb).operateOnByteBufferWriteOut(toWrite, op);
            proxyHandle.len -= wrote;
            if (proxyHandle.len == 0) {
                proxyHandle.proxyDone();
                proxyHandle = null;
            }
            return wrote;
        } else {
            int wrote = defaultBuffer.operateOnByteBufferWriteOut(maxBytesToWrite, op);
            if (defaultBuffer.used() == 0 && proxyHandle != null) {
                assert Logger.lowLevelDebug("wrote all data from defaultBuffer, switch to proxy mode");
                proxyHandle.enabled = true;
            }
            if (wrote == maxBytesToWrite || proxyHandle == null)
                return wrote;
            return wrote + operateOnByteBufferWriteOut(maxBytesToWrite - wrote, op);
        }
    }

    @Override
    public int operateOnByteBufferStoreIn(StoreInOp op) throws IOException {
        if (proxyHandle != null)
            throw new IllegalStateException("already has proxyHandle, with proxyLen = " + proxyHandle.len);
        return defaultBuffer.operateOnByteBufferStoreIn(op);
    }

    @Override
    public boolean canDefragment() {
        if (proxyHandle == null || !proxyHandle.enabled) {
            return defaultBuffer.canDefragment();
        }
        return ((ByteBufferRingBuffer) proxyHandle.rb).canDefragment();
    }

    @Override
    public void defragment() {
        if (proxyHandle == null || !proxyHandle.enabled) {
            defaultBuffer.defragment();
            return;
        }
        ((ByteBufferRingBuffer) proxyHandle.rb).defragment();
    }
}
