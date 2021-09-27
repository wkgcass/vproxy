package io.vproxy.base.util.unsafe;

import jdk.internal.misc.Unsafe;

public class JDKUnsafeImpl implements JDKUnsafe{
    private static final Unsafe unsafe = Unsafe.getUnsafe();

    public byte[] allocateUninitialized0(int len) {
        return (byte[]) unsafe.allocateUninitializedArray(byte.class, len);
    }
}
