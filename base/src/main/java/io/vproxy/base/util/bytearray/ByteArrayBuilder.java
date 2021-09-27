package vproxy.base.util.bytearray;

import vproxy.base.util.ByteArray;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ByteArrayBuilder extends AbstractByteArray implements ByteArray {
    private static final int DEFAULT_PAGE_SIZE = 16;
    private static final int MAXIMUM_CAPACITY = 1 << 30;
    private final List<byte[]> bytePages = new ArrayList<>();
    private final int pageSize;
    private int pageIdx;
    private int arrayIdx;

    public ByteArrayBuilder() {
        this(DEFAULT_PAGE_SIZE);
    }

    /**
     * @param pageSize MUST be greater than zero
     */
    public ByteArrayBuilder(int pageSize) {
        if (pageSize <= 0) {
            throw new RuntimeException("'pageSize' MUST be greater than zero");
        }

        this.pageSize = tableSizeFor(pageSize);
        this.pageIdx = -1;
        this.arrayIdx = this.pageSize;
    }

    /**
     * Copy from {@code java.util.HashMap#tableSizeFor(int)}
     * <br>
     * Returns a power of two size for the given target capacity.
     */
    static int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    public ByteArrayBuilder append(byte b) {
        // add new page and reset the index
        if (arrayIdx >= pageSize) {
            pageIdx++;
            arrayIdx = 0;
            bytePages.add(new byte[pageSize]);
        }

        bytePages.get(pageIdx)[arrayIdx++] = b;
        return this;
    }

    public ByteArrayBuilder append(int... b) {
        for (int b1 : b) {
            this.append((byte) b1);
        }
        return this;
    }

    private int calcPageIdx(int idx) {
        return idx / pageSize;
    }

    private int calcArrayIdx(int idx) {
        return idx % pageSize;
    }

    @Override
    public byte get(int idx) {
        return bytePages.get(calcPageIdx(idx))[calcArrayIdx(idx)];
    }

    @Override
    public ByteArray set(int idx, byte value) {
        bytePages.get(calcPageIdx(idx))[calcArrayIdx(idx)] = value;
        return this;
    }

    @Override
    public int length() {
        return pageIdx * pageSize + arrayIdx;
    }

    @Override
    protected void doToNewJavaArray(byte[] dst, int dstOff, int srcOff, int srcLen) {
        int startPageIdx = calcPageIdx(srcOff);
        int startArrayIdx = calcArrayIdx(srcOff);
        int endPageIdx = calcPageIdx(srcOff + srcLen - 1);
        int endArrayIdx = calcArrayIdx(srcOff + srcLen - 1);

        for (int page = startPageIdx; page <= endPageIdx; page++) {
            byte[] array = bytePages.get(page);
            int start = page == startPageIdx ? startArrayIdx : 0;
            int end = page == endPageIdx ? endArrayIdx + 1 : pageSize;
            int length = end - start;
            System.arraycopy(array, start, dst, dstOff, length);
            dstOff += length;
        }
    }

    @Override
    public void byteBufferPut(ByteBuffer dst, int off, int len) {
        for (int i = off; i < off + len; i++) {
            dst.put(this.get(i));
        }
    }

    @Override
    public void byteBufferGet(ByteBuffer src, int off, int len) {
        for (int i = off; i < off + len; i++) {
            this.set(i, src.get());
        }
    }
}
