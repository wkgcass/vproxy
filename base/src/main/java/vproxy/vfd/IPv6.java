package vproxy.vfd;

import vproxy.base.util.ByteArray;

import java.net.Inet6Address;
import java.util.Objects;

public class IPv6 extends IP {
    private final int value0;
    private final int value1;
    private final int value2;
    private final int value3;

    IPv6(byte[] bytes) {
        super(ByteArray.from(bytes));
        value0 = this.bytes.int32(0);
        value1 = this.bytes.int32(4);
        value2 = this.bytes.int32(8);
        value3 = this.bytes.int32(12);
    }

    @Override
    public Inet6Address toInetAddress() {
        return (Inet6Address) super.toInetAddress();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (!(o instanceof IPv6)) return false;
        IPv6 that = (IPv6) o;
        return value0 == that.value0 && value1 == that.value1 && value2 == that.value2 && value3 == that.value3;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value0, value1, value2, value3);
    }

    @Override
    public boolean isBroadcast() {
        return false;
    }

    @Override
    public boolean isMulticast() {
        return bytes.get(0) == (byte) 0xff;
    }
}
