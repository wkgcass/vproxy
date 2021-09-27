package io.vproxy.base.selector.wrap.kcp.mock;

import io.vproxy.base.util.nio.ByteArrayChannel;

public class ByteBufAllocator {
    public static final ByteBufAllocator DEFAULT = new ByteBufAllocator();

    public ByteBuf ioBuffer(int a, int b) {
        if (a == 0 && b == 0) {
            return new ByteBuf(ByteArrayChannel.zero());
        }
        throw new UnsupportedOperationException();
    }

    public ByteBuf ioBuffer(int size) {
        if (size == 0) {
            return new ByteBuf(ByteArrayChannel.zero());
        } else {
            return new ByteBuf(ByteArrayChannel.fromEmpty(size));
        }
    }
}
