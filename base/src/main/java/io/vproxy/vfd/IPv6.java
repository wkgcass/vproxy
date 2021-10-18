package io.vproxy.vfd;

import io.vproxy.base.util.ByteArray;

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

    public int getIPv6Value0() {
        return value0;
    }

    public int getIPv6Value1() {
        return value1;
    }

    public int getIPv6Value2() {
        return value2;
    }

    public int getIPv6Value3() {
        return value3;
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

    public String formatToIPStringWithoutBrackets() {
        String ipv6 = formatToIPString();
        if (ipv6.startsWith("[")) {
            ipv6 = ipv6.substring(1, ipv6.length() - 1);
        }
        return ipv6;
    }

    @Override
    public boolean isBroadcast() {
        return false;
    }

    @Override
    public boolean isMulticast() {
        return bytes.get(0) == (byte) 0xff;
    }

    public boolean isV4MappedV6Address() {
        return value0 == 0 && value1 == 0 && value2 == 0xffff;
    }

    public boolean isV4CompatibleV6Address() {
        return value0 == 0 && value1 == 0 && value2 == 0;
    }
}
