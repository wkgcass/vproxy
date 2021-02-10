package vproxybase.util.unsafe;

public class JDKUnsafeFallback implements JDKUnsafe {
    @Override
    public byte[] allocateUninitialized0(int len) {
        return new byte[len];
    }
}
