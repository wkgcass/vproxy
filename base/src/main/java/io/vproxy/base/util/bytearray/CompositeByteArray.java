package io.vproxy.base.util.bytearray;

import io.vproxy.base.util.ByteArray;

import java.nio.ByteBuffer;

public class CompositeByteArray extends AbstractByteArray implements ByteArray {
    private final ByteArray first;
    private final ByteArray second;
    private final int firstLen;
    private final int len;
    private final boolean backingSizeStable;
    private final long stableBackingSize;

    /**
     * Join two byte arrays while keeping the composite tree logarithmic in the
     * number of leaves. The children themselves remain shared and mutable.
     */
    public static ByteArray concatBalanced(ByteArray first, ByteArray second) {
        return join(first, second);
    }

    private static ByteArray join(ByteArray first, ByteArray second) {
        int firstDepth = first.treeDepth();
        int secondDepth = second.treeDepth();
        if (firstDepth > secondDepth + 1 && first instanceof CompositeByteArray composite) {
            return rebalance(composite.first, join(composite.second, second));
        }
        if (secondDepth > firstDepth + 1 && second instanceof CompositeByteArray composite) {
            return rebalance(join(first, composite.first), composite.second);
        }
        return new CompositeByteArray(first, second);
    }

    private static ByteArray rebalance(ByteArray first, ByteArray second) {
        int firstDepth = first.treeDepth();
        int secondDepth = second.treeDepth();
        if (firstDepth > secondDepth + 1 && first instanceof CompositeByteArray left) {
            if (left.first.treeDepth() >= left.second.treeDepth()) {
                return new CompositeByteArray(left.first, new CompositeByteArray(left.second, second));
            }
            if (left.second instanceof CompositeByteArray middle) {
                return new CompositeByteArray(
                    new CompositeByteArray(left.first, middle.first),
                    new CompositeByteArray(middle.second, second));
            }
        } else if (secondDepth > firstDepth + 1 && second instanceof CompositeByteArray right) {
            if (right.second.treeDepth() >= right.first.treeDepth()) {
                return new CompositeByteArray(new CompositeByteArray(first, right.first), right.second);
            }
            if (right.first instanceof CompositeByteArray middle) {
                return new CompositeByteArray(
                    new CompositeByteArray(first, middle.first),
                    new CompositeByteArray(middle.second, right.second));
            }
        }
        return new CompositeByteArray(first, second);
    }

    public CompositeByteArray(ByteArray first, ByteArray second) {
        this.first = first;
        this.second = second;
        this.firstLen = first.length();
        this.len = Math.addExact(firstLen, second.length());
        this.backingSizeStable = first.retainedBackingSizeIsStable() && second.retainedBackingSizeIsStable();
        this.stableBackingSize = first.retainedBackingSize() + second.retainedBackingSize();
    }

    public ByteArray getFirst() {
        return first;
    }

    public ByteArray getSecond() {
        return second;
    }

    @Override
    public byte get(int idx) {
        checkBoundForOffset(idx);
        if (idx < firstLen) {
            return first.get(idx);
        } else {
            return second.get(idx - firstLen);
        }
    }

    @Override
    public ByteArray set(int idx, byte value) {
        checkBoundForOffset(idx);
        if (idx < firstLen) {
            first.set(idx, value);
        } else {
            second.set(idx - firstLen, value);
        }
        return this;
    }

    @Override
    public int length() {
        return len;
    }

    @Override
    public int treeDepth() {
        return 1 + Math.max(first.treeDepth(), second.treeDepth());
    }

    @Override
    public long retainedBackingSize() {
        if (backingSizeStable) {
            return stableBackingSize;
        }
        return first.retainedBackingSize() + second.retainedBackingSize();
    }

    @Override
    public boolean retainedBackingSizeIsStable() {
        return backingSizeStable;
    }

    @Override
    boolean supportsDirectArrayCopy() {
        return first instanceof AbstractByteArray firstArray && firstArray.supportsDirectArrayCopy()
            && second instanceof AbstractByteArray secondArray && secondArray.supportsDirectArrayCopy();
    }

    @Override
    boolean sharesBacking(SimpleByteArray target) {
        return first instanceof AbstractByteArray firstArray && firstArray.sharesBacking(target)
            || second instanceof AbstractByteArray secondArray && secondArray.sharesBacking(target);
    }

    @Override
    public void copyInto(ByteArray dst, int dstOff, int srcOff, int srcLen) {
        if (!(dst instanceof SimpleByteArray target) || sharesBacking(target)
            || !supportsDirectArrayCopy()
            || !validRange(srcOff, srcLen, len) || !validRange(dstOff, srcLen, target.length())) {
            copyIntoSlow(dst, dstOff, srcOff, srcLen);
            return;
        }
        target.copyFrom(this, dstOff, srcOff, srcLen);
    }

    private boolean validRange(int off, int size, int total) {
        return off >= 0 && size >= 0 && off <= total && size <= total - off;
    }

    @Override
    protected void checkBoundForOffset(int off) {
        if (off < 0) {
            throw new IllegalArgumentException("off=" + off + " < 0");
        }
        if (off >= len) {
            throw new ArrayIndexOutOfBoundsException("off=" + off + ", length=" + len);
        }
    }

    @Override
    protected void checkBoundForOffsetAndLength(int off, int size) {
        if (size < 0) {
            throw new IllegalArgumentException("len=" + size + " < 0");
        }
        if (size > 0) {
            checkBoundForOffset(off);
        }
        if (off > len || (size > 0 && size > len - off)) {
            throw new ArrayIndexOutOfBoundsException("off=" + off + ", len=" + size + ", length=" + len);
        }
    }

    @Override
    public int uint16(int offset) {
        checkBoundForOffsetAndLength(offset, 2);
        if (offset + 2 <= firstLen) return first.uint16(offset);
        if (offset >= firstLen) return second.uint16(offset - firstLen);
        return ((first.get(offset) & 0xff) << 8) | (second.get(0) & 0xff);
    }

    @Override
    public int uint16ReverseNetworkByteOrder(int offset) {
        checkBoundForOffsetAndLength(offset, 2);
        if (offset + 2 <= firstLen) return first.uint16ReverseNetworkByteOrder(offset);
        if (offset >= firstLen) return second.uint16ReverseNetworkByteOrder(offset - firstLen);
        return ((second.get(0) & 0xff) << 8) | (first.get(offset) & 0xff);
    }

    @Override
    public int int32(int offset) {
        checkBoundForOffsetAndLength(offset, 4);
        if (offset + 4 <= firstLen) return first.int32(offset);
        if (offset >= firstLen) return second.int32(offset - firstLen);
        return (uint8(offset) << 24) | (uint8(offset + 1) << 16) | (uint8(offset + 2) << 8) | uint8(offset + 3);
    }

    @Override
    public int int32ReverseNetworkByteOrder(int offset) {
        checkBoundForOffsetAndLength(offset, 4);
        if (offset + 4 <= firstLen) return first.int32ReverseNetworkByteOrder(offset);
        if (offset >= firstLen) return second.int32ReverseNetworkByteOrder(offset - firstLen);
        return (uint8(offset + 3) << 24) | (uint8(offset + 2) << 16) | (uint8(offset + 1) << 8) | uint8(offset);
    }

    @Override
    public long uint32(int offset) {
        return int32(offset) & 0xffffffffL;
    }

    @Override
    public long uint32ReverseNetworkByteOrder(int offset) {
        return int32ReverseNetworkByteOrder(offset) & 0xffffffffL;
    }

    @Override
    public long int64(int offset) {
        checkBoundForOffsetAndLength(offset, 8);
        if (offset + 8 <= firstLen) return first.int64(offset);
        if (offset >= firstLen) return second.int64(offset - firstLen);
        long result = 0;
        for (int i = 0; i < 8; ++i) result = (result << 8) | uint8(offset + i);
        return result;
    }

    @Override
    public long int64ReverseNetworkByteOrder(int offset) {
        checkBoundForOffsetAndLength(offset, 8);
        if (offset + 8 <= firstLen) return first.int64ReverseNetworkByteOrder(offset);
        if (offset >= firstLen) return second.int64ReverseNetworkByteOrder(offset - firstLen);
        long result = 0;
        for (int i = 7; i >= 0; --i) result = (result << 8) | uint8(offset + i);
        return result;
    }

    @Override
    public int uint24(int offset) {
        checkBoundForOffsetAndLength(offset, 3);
        return (uint8(offset) << 16) | (uint8(offset + 1) << 8) | uint8(offset + 2);
    }

    @Override
    public int uint24ReverseNetworkByteOrder(int offset) {
        checkBoundForOffsetAndLength(offset, 3);
        return (uint8(offset + 2) << 16) | (uint8(offset + 1) << 8) | uint8(offset);
    }

    @Override
    public long updateChecksum(long state, int off, int size) {
        checkBoundForOffsetAndLength(off, size);
        int from = off;
        int remaining = size;
        if (from < firstLen) {
            int count = Math.min(remaining, firstLen - from);
            state = first.updateChecksum(state, from, count);
            from = 0;
            remaining -= count;
        } else {
            from -= firstLen;
        }
        if (remaining > 0) {
            state = second.updateChecksum(state, from, remaining);
        }
        return state;
    }

    @Override
    public void byteBufferPut(ByteBuffer dst, int off, int len) {
        checkBoundForByteBufferAndOffsetAndLength(dst, off, len);

        if (off > firstLen) {
            second.byteBufferPut(dst, off - firstLen, len);
            return;
        }
        if (len < firstLen - off) {
            first.byteBufferPut(dst, off, len);
            return;
        }
        first.byteBufferPut(dst, off, firstLen - off);
        second.byteBufferPut(dst, 0, len - (firstLen - off));
    }

    @Override
    public void byteBufferGet(ByteBuffer src, int off, int len) {
        checkBoundForByteBufferAndOffsetAndLength(src, off, len);

        if (off > firstLen) {
            second.byteBufferGet(src, off - firstLen, len);
            return;
        }
        if (len < firstLen - off) {
            first.byteBufferGet(src, off, len);
            return;
        }
        first.byteBufferGet(src, off, firstLen - off);
        second.byteBufferGet(src, 0, len - (firstLen - off));
    }

    @Override
    protected void doToNewJavaArray(byte[] dst, int dstOff, int srcOff, int srcLen) {
        if (firstLen > srcOff) {
            //noinspection UnnecessaryLocalVariable
            int off = srcOff;
            int len = Math.min(srcLen, firstLen - off);
            ((AbstractByteArray) first).doToNewJavaArray(dst, dstOff, off, len);
            if (srcLen > len) {
                ((AbstractByteArray) second).doToNewJavaArray(dst, dstOff + len, 0, srcLen - len);
            }
        } else {
            ((AbstractByteArray) second).doToNewJavaArray(dst, dstOff, srcOff - firstLen, srcLen);
        }
    }
}
