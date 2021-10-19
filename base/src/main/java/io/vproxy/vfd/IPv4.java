package io.vproxy.vfd;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Utils;

import java.net.Inet4Address;

public class IPv4 extends IP {
    private final int value;

    IPv4(byte[] bytes) {
        super(ByteArray.from(bytes));
        value = this.bytes.int32(0);
    }

    @Override
    public Inet4Address toInetAddress() {
        return (Inet4Address) super.toInetAddress();
    }

    public int getIPv4Value() {
        return value;
    }

    public int maskValue(int maskNumber) {
        return value & Utils.maskNumberToInt(maskNumber);
    }

    @Override
    public IPv4 to4() {
        return this;
    }

    @Override
    public IPv6 to6() {
        return new IPv6(ByteArray.allocateInitZero(16)
            .int16(10, 0xffff).int32(12, value).toJavaArray());
    }

    @Override
    public boolean equals(Object that) {
        if (that == null) return false;
        if (!(that instanceof IPv4)) return false;
        return value == ((IPv4) that).value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public boolean isBroadcast() {
        return bytes.get(0) == (byte) 0xff && bytes.get(1) == (byte) 0xff && bytes.get(2) == (byte) 0xff && bytes.get(3) == (byte) 0xff;
    }

    @Override
    public boolean isMulticast() {
        return (bytes.get(0) & 0b11100000) == 0b11100000;
    }
}
