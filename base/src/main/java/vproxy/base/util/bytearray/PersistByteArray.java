package vproxy.base.util.bytearray;

import vproxy.base.util.ByteArray;

public class PersistByteArray extends UnmodifiableByteArray {
    private final String toString;
    private final int hashCode;

    public PersistByteArray(ByteArray array) {
        super(ByteArray.from(array.toJavaArray()));
        hashCode = super.hashCode();
        toString = super.toString();
    }

    @Override
    public byte[] toJavaArray() {
        return super.toNewJavaArray();
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return toString;
    }
}
