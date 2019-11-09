package vproxy.selector.wrap.kcp.mock;

import vproxy.util.Utils;
import vproxy.util.nio.ByteArrayChannel;

import java.nio.ByteBuffer;

public class ByteBuf {
    public final ByteArrayChannel chnl;

    public ByteBuf(ByteArrayChannel chnl) {
        this.chnl = chnl;
    }

    public int readableBytes() {
        return chnl.used();
    }

    public int writerIndex() {
        return chnl.getWriteOff();
    }

    public void writeIntLE(int i) {
        byte b1 = (byte) ((i >> 24) & 0xff);
        byte b2 = (byte) ((i >> 16) & 0xff);
        byte b3 = (byte) ((i >> 8) & 0xff);
        byte b4 = (byte) (i & 0xff);
        chnl.write(ByteBuffer.wrap(new byte[]{b4, b3, b2, b1}));
    }

    public void writeByte(byte b) {
        chnl.write(ByteBuffer.wrap(new byte[]{b}));
    }

    public void writeByte(short n) {
        chnl.write(ByteBuffer.wrap(new byte[]{(byte) (n & 0xff)}));
    }

    public void writeShortLE(int n) {
        byte b1 = (byte) ((n >> 8) & 0xff);
        byte b2 = (byte) (n & 0xff);
        chnl.write(ByteBuffer.wrap(new byte[]{b2, b1}));
    }

    public int maxCapacity() {
        return chnl.used() + chnl.free();
    }

    private ByteBuffer byteBuffer() {
        return ByteBuffer.wrap(chnl.getBytes(), 0, chnl.used());
    }

    public void writeBytes(ByteBuf b) {
        chnl.write(b.byteBuffer());
    }

    public void writeBytes(ByteBuf b, @SuppressWarnings("unused") int extend) {
        chnl.write(b.byteBuffer());
    }

    public void writeBytes(ByteBuf src, int srcIndex, int length) {
        chnl.write(ByteBuffer.wrap(src.chnl.getBytes(), srcIndex, length));
    }

    public int maxWritableBytes() {
        return chnl.free();
    }

    public ByteBuf readRetainedSlice(int len) {
        ByteBuf b = new ByteBuf(ByteArrayChannel.from(
            chnl.getBytes(), chnl.getReadOff(), chnl.getReadOff() + len, 0
        ));
        chnl.skip(len);
        return b;
    }

    public int readIntLE() {
        int b1 = Utils.positive(chnl.read());
        int b2 = Utils.positive(chnl.read());
        int b3 = Utils.positive(chnl.read());
        int b4 = Utils.positive(chnl.read());
        return (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
    }

    public byte readByte() {
        return chnl.read();
    }

    public short readUnsignedByte() {
        byte b = chnl.read();
        return (short) Utils.positive(b);
    }

    public int readUnsignedShortLE() {
        int b1 = Utils.positive(chnl.read());
        int b2 = Utils.positive(chnl.read());
        short s = (short) ((b2 << 8) | b1);
        return Utils.positive(s);
    }

    public int readUnsignedIntLE() {
        return readIntLE();
    }

    public void skipBytes(int len) {
        for (int i = 0; i < len; ++i) {
            chnl.read();
        }
    }

    public int readerIndex() {
        return chnl.getReadOff();
    }

    public void release() {
        // do nothing
    }
}
