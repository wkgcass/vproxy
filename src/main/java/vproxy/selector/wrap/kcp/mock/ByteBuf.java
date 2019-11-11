package vproxy.selector.wrap.kcp.mock;

import vproxy.util.Utils;
import vproxy.util.nio.ByteArrayChannel;

import java.nio.ByteBuffer;

public class ByteBuf {
    public final ByteArrayChannel chnl;

    public ByteBuf(ByteArrayChannel chnl) {
        this.chnl = chnl;
    }

    /*
     * Returns the number of readable bytes which is equal to (this.writerIndex - this.readerIndex).
     */
    public int readableBytes() {
        return chnl.used();
    }

    /*
     * Returns the writerIndex of this buffer.
     */
    public int writerIndex() {
        return chnl.getWriteOff();
    }

    private void checkWriteBound(int writeLen) {
        if (chnl.free() < writeLen) {
            throw new IndexOutOfBoundsException("chnl.free = " + chnl.free() + " < " + writeLen);
        }
    }

    /*
     * Sets the specified 32-bit integer at the current writerIndex in the Little Endian Byte Order and increases the writerIndex by 4 in this buffer.
     * If this.writableBytes is less than 4, ensureWritable(int) will be called in an attempt to expand capacity to accommodate.
     */
    public void writeIntLE(int i) {
        checkWriteBound(4);
        byte b1 = (byte) ((i >> 24) & 0xff);
        byte b2 = (byte) ((i >> 16) & 0xff);
        byte b3 = (byte) ((i >> 8) & 0xff);
        byte b4 = (byte) (i & 0xff);
        chnl.write(ByteBuffer.wrap(new byte[]{b4, b3, b2, b1}));
    }

    /*
     * Sets the specified byte at the current writerIndex and increases the writerIndex by 1 in this buffer. The 24 high-order bits of the specified value are ignored.
     * If this.writableBytes is less than 1, ensureWritable(int) will be called in an attempt to expand capacity to accommodate.
     */
    public void writeByte(int b) {
        checkWriteBound(1);
        chnl.write(ByteBuffer.wrap(new byte[]{(byte) (b & 0xff)}));
    }

    /*
     * Sets the specified 16-bit short integer at the current writerIndex and increases the writerIndex by 2 in this buffer. The 16 high-order bits of the specified value are ignored.
     * If this.writableBytes is less than 2, ensureWritable(int) will be called in an attempt to expand capacity to accommodate.
     */
    public void writeShortLE(int n) {
        checkWriteBound(2);
        byte b1 = (byte) ((n >> 8) & 0xff);
        byte b2 = (byte) (n & 0xff);
        chnl.write(ByteBuffer.wrap(new byte[]{b2, b1}));
    }

    /*
     * Returns the maximum allowed capacity of this buffer. This value provides an upper bound on capacity().
     */
    public int maxCapacity() {
        return chnl.getWriteOff() + chnl.getWriteLen();
    }

    /*
     * Transfers the specified source buffer's data to this buffer starting at the current writerIndex until the source buffer becomes unreadable,
     * and increases the writerIndex by the number of the transferred bytes.
     * This method is basically same with writeBytes(ByteBuf, int, int), except that this method increases the readerIndex of the source buffer by the number of the transferred bytes while writeBytes(ByteBuf, int, int) does not.
     * If this.writableBytes is less than src.readableBytes, ensureWritable(int) will be called in an attempt to expand capacity to accommodate.
     */
    public void writeBytes(ByteBuf b) {
        writeBytes(b, b.chnl.used());
    }

    /*
     * Transfers the specified source buffer's data to this buffer starting at the current writerIndex and increases the writerIndex by the number of the transferred bytes (= length).
     * This method is basically same with writeBytes(ByteBuf, int, int), except that this method increases the readerIndex of the source buffer by the number of the transferred bytes (= length) while writeBytes(ByteBuf, int, int) does not.
     * If this.writableBytes is less than length, ensureWritable(int) will be called in an attempt to expand capacity to accommodate.
     */
    public void writeBytes(ByteBuf b, int len) {
        checkWriteBound(len);

        // write into chnl
        writeBytes(b, b.chnl.getReadOff(), len);
        // modify b
        b.chnl.skip(len);
    }

    /*
     * Transfers the specified source buffer's data to this buffer starting at the current writerIndex and increases the writerIndex by the number of the transferred bytes (= length).
     * If this.writableBytes is less than length, ensureWritable(int) will be called in an attempt to expand capacity to accommodate.
     */
    public void writeBytes(ByteBuf src, int srcIndex, int length) {
        checkWriteBound(length);

        chnl.write(ByteArrayChannel.fromFull(src.chnl.getArray().sub(srcIndex, length)));
    }

    /*
     * Returns the maximum possible number of writable bytes, which is equal to (this.maxCapacity - this.writerIndex).
     */
    public int maxWritableBytes() {
        return chnl.free();
    }

    /*
     * Returns a new retained slice of this buffer's sub-region starting at the current readerIndex and increases the readerIndex by the size of the new slice (= length).
     * Note that this method returns a retained buffer unlike readSlice(int).
     * This method behaves similarly to readSlice(...).retain() except that this method may return a buffer implementation that produces less garbage.
     */
    public ByteBuf readRetainedSlice(int len) {
        ByteBuf b = new ByteBuf(ByteArrayChannel.fromFull(
            chnl.getArray().sub(chnl.getReadOff(), len)
        ));
        chnl.skip(len);
        return b;
    }

    /*
     * Gets a 32-bit integer at the current readerIndex in the Little Endian Byte Order and increases the readerIndex by 4 in this buffer.
     */
    public int readIntLE() {
        int b1 = Utils.positive(chnl.read());
        int b2 = Utils.positive(chnl.read());
        int b3 = Utils.positive(chnl.read());
        int b4 = Utils.positive(chnl.read());
        return (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
    }

    /*
     * Gets a byte at the current readerIndex and increases the readerIndex by 1 in this buffer.
     */
    public byte readByte() {
        return chnl.read();
    }

    /*
     * Gets an unsigned byte at the current readerIndex and increases the readerIndex by 1 in this buffer.
     */
    public short readUnsignedByte() {
        return (short) (readByte() & 0xFF);
    }

    /*
     * Gets an unsigned 16-bit short integer at the current readerIndex in the Little Endian Byte Order and increases the readerIndex by 2 in this buffer.
     */
    public int readUnsignedShortLE() {
        int b1 = Utils.positive(chnl.read());
        int b2 = Utils.positive(chnl.read());
        short s = (short) ((b2 << 8) | b1);
        return s & 0xFFFF;
    }

    /*
     * Gets an unsigned 32-bit integer at the current readerIndex in the Little Endian Byte Order and increases the readerIndex by 4 in this buffer.
     */
    public long readUnsignedIntLE() {
        return readIntLE() & 0xFFFFFFFFL;
    }

    /*
     * Increases the current readerIndex by the specified length in this buffer.
     */
    public void skipBytes(int len) {
        chnl.skip(len);
    }

    /*
     * Returns the readerIndex of this buffer.
     */
    public int readerIndex() {
        return chnl.getReadOff();
    }

    public void release() {
        // do nothing
    }

    @Override
    public String toString() {
        return chnl.toString();
    }
}
