package net.cassite.vproxy.util.bytearray;

import net.cassite.vproxy.util.ByteArray;

public class CompositeByteArray extends AbstractByteArray implements ByteArray {
    private final ByteArray first;
    private final ByteArray second;
    private final int len;

    public CompositeByteArray(ByteArray first, ByteArray second) {
        this.first = first;
        this.second = second;

        this.len = first.length() + second.length();
    }

    @Override
    public byte get(int idx) {
        if (idx >= len || idx < 0)
            throw new ArrayIndexOutOfBoundsException("index=" + idx + ", length=" + len);
        if (idx < first.length()) {
            return first.get(idx);
        } else {
            return second.get(idx - first.length());
        }
    }

    @Override
    public ByteArray set(int idx, byte value) {
        if (idx >= len || idx < 0)
            throw new ArrayIndexOutOfBoundsException("index=" + idx + ", length=" + len);
        if (idx < first.length()) {
            first.set(idx, value);
        } else {
            second.set(idx - first.length(), value);
        }
        return this;
    }

    @Override
    public int length() {
        return len;
    }
}
