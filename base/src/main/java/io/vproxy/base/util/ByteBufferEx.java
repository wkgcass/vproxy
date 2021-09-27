package io.vproxy.base.util;

import java.nio.ByteBuffer;

public class ByteBufferEx {
    protected final int cap;
    protected final ByteBuffer buffer;

    public ByteBufferEx(ByteBuffer buffer) {
        this.buffer = buffer;
        cap = buffer.capacity();
    }

    public ByteBuffer realBuffer() {
        return buffer;
    }

    public void clean() {
    }

    public int capacity() {
        return buffer.capacity();
    }

    public int limit() {
        return buffer.limit();
    }

    public ByteBufferEx limit(int limit) {
        buffer.limit(limit);
        return this;
    }

    public int position() {
        return buffer.position();
    }

    public ByteBufferEx position(int position) {
        buffer.position(position);
        return this;
    }

    public ByteBufferEx get(byte[] dst, int off, int len) {
        buffer.get(dst, off, len);
        return this;
    }

    public ByteBufferEx put(ByteBufferEx src) {
        buffer.put(src.buffer);
        return this;
    }

    public ByteBufferEx put(byte[] bytes) {
        buffer.put(bytes);
        return this;
    }

    public ByteBufferEx flip() {
        buffer.flip();
        return this;
    }

    public ByteBufferEx putLong(long l) {
        buffer.putLong(l);
        return this;
    }

    public ByteBufferEx get(byte[] array) {
        buffer.get(array);
        return this;
    }
}
