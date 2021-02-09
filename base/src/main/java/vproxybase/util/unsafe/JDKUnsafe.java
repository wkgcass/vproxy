package vproxybase.util.unsafe;

import jdk.internal.misc.Unsafe;

public class JDKUnsafe {
    private static final Unsafe unsafe = Unsafe.getUnsafe();

    public static byte[] allocateUninitializedByteArray(int len) {
        return (byte[]) unsafe.allocateUninitializedArray(byte.class, len);
    }
}
