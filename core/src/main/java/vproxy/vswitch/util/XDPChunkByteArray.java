package vproxy.vswitch.util;

import vproxy.base.util.ByteArray;
import vproxy.base.util.bytearray.AbstractByteArray;
import vproxy.xdp.Chunk;
import vproxy.xdp.NativeXDP;
import vproxy.xdp.XDPSocket;

import java.nio.ByteBuffer;

public class XDPChunkByteArray extends AbstractByteArray implements ByteArray {
    public final ByteBuffer buffer;
    public final int off;
    public final int len;

    public final XDPSocket xsk;
    public final Chunk chunk;

    public XDPChunkByteArray(XDPSocket xsk, Chunk chunk) {
        ByteBuffer buffer = xsk.umem.getBuffer();
        int off = chunk.addr();
        int len = chunk.endaddr() - chunk.addr();

        this.buffer = buffer;
        this.off = off;
        this.len = len;

        if (buffer.capacity() < off + len) {
            throw new IllegalArgumentException("buffer.cap=" + buffer.capacity() + ", off=" + off + ", len=" + len);
        }
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("buffer is not direct");
        }

        this.xsk = xsk;
        this.chunk = chunk;
    }

    public void releaseRef() {
        chunk.releaseRef(xsk.umem);
    }

    @Override
    public byte get(int idx) {
        if (len <= idx) {
            throw new IndexOutOfBoundsException("len=" + len + ", idx=" + idx);
        }
        int n = off + idx;
        if (n >= buffer.limit()) {
            buffer.limit(buffer.capacity());
        }
        return buffer.get(n);
    }

    @Override
    public ByteArray set(int idx, byte value) {
        if (len <= idx) {
            throw new IndexOutOfBoundsException("len=" + len + ", idx=" + idx);
        }
        int n = off + idx;
        if (n >= buffer.limit()) {
            buffer.limit(buffer.capacity());
        }
        buffer.put(n, value);
        return this;
    }

    @Override
    public int length() {
        return len;
    }

    @Override
    public void byteBufferPut(ByteBuffer dst, int off, int len) {
        if (this.off + off + len > buffer.capacity()) {
            throw new IndexOutOfBoundsException("buffer.cap=" + buffer.capacity() + ", this.off=" + this.off + ", off=" + off + ", len=" + len);
        }
        if (dst == buffer) {
            if (dst.position() == this.off + off) {
                // same memory region, nothing to be copied
                dst.position(dst.position() + len);
            } else {
                NativeXDP.get().utilCopyMemory(xsk.umem.umem, this.off + off, dst.position(), len);
            }
            return;
        }

        buffer.limit(this.off + off + len).position(this.off + off);
        dst.put(buffer);
    }

    @Override
    public void byteBufferGet(ByteBuffer src, int off, int len) {
        if (this.off + off + len > buffer.capacity()) {
            throw new IndexOutOfBoundsException("buffer.cap=" + buffer.capacity() + ", this.off=" + this.off + ", off=" + off + ", len=" + len);
        }
        if (src.limit() - src.position() < len) {
            throw new IndexOutOfBoundsException("src.lim=" + src.limit() + ", src.pos=" + src.position() + ", len=" + len);
        }
        if (src == buffer) {
            if (src.position() == this.off + off) {
                // same memory region, nothing to be copied
                src.position(src.position() + len);
            } else {
                NativeXDP.get().utilCopyMemory(xsk.umem.umem, src.position(), this.off + off, len);
            }
            return;
        }

        int srcLim = src.limit();

        buffer.limit(this.off + off + len).position(this.off + off);
        src.limit(src.position() + len);
        buffer.put(src);

        src.limit(srcLim);
    }

    @Override
    protected void doToNewJavaArray(byte[] dst, int dstOff, int srcOff, int srcLen) {
        buffer.limit(buffer.capacity()).position(off + srcOff);
        buffer.get(dst, dstOff, srcLen);
    }
}
