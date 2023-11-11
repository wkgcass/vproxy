package io.vproxy.msquic;

import io.vproxy.base.util.bytearray.MemorySegmentByteArray;

public class QuicBufferByteArray extends MemorySegmentByteArray {
    private QuicBufferByteArray(QuicBuffer quicBuffer) {
        super(quicBuffer.getBuffer().reinterpret(quicBuffer.getLength()));
    }

    public static QuicBufferByteArray of(QuicBuffer quicBuffer) {
        if (quicBuffer.getBuffer() == null) return null;
        return new QuicBufferByteArray(quicBuffer);
    }
}
