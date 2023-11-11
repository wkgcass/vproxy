package io.vproxy.vfd;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Utils;

import java.net.Inet6Address;
import java.util.Objects;

public class IPv6 extends IP {
    private final int value0;
    private final int value1;
    private final int value2;
    private final int value3;

    IPv6(String hostname, byte[] bytes) {
        super(hostname, ByteArray.from(bytes));
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

    public Values getIPv6Values() {
        return new IPv6.Values(value0, value1, value2, value3);
    }

    public Values maskValues(int maskNumber) {
        //noinspection UnnecessaryLocalVariable
        final int mask = maskNumber;
        if (mask >= 128) {
            return new Values(value0, value1, value2, value3);
        } else if (mask > 96) {
            return new Values(value0, value1, value2, value3 & Utils.maskNumberToInt(mask - 96));
        } else if (mask > 64) {
            return new Values(value0, value1, value2 & Utils.maskNumberToInt(mask - 64), 0);
        } else if (mask > 32) {
            return new Values(value0, value1 & Utils.maskNumberToInt(mask - 32), 0, 0);
        } else {
            return new Values(value0 & Utils.maskNumberToInt(mask), 0, 0, 0);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (!(o instanceof IPv6)) return false;
        IPv6 that = (IPv6) o;
        return value0 == that.value0 && value1 == that.value1 && value2 == that.value2 && value3 == that.value3 && Objects.equals(hostname, that.hostname);
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

    // might be null
    @Override
    public IPv4 to4() {
        if (isV4MappedV6Address() || isV4CompatibleV6Address()) {
            return new IPv4(hostname, ByteArray.allocate(4).int32(0, value3).toJavaArray());
        }
        return null;
    }

    @Override
    public IPv6 to6() {
        return this;
    }

    public static final class Values {
        public final int value0;
        public final int value1;
        public final int value2;
        public final int value3;

        public Values(int value0, int value1, int value2, int value3) {
            this.value0 = value0;
            this.value1 = value1;
            this.value2 = value2;
            this.value3 = value3;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Values values = (Values) o;
            return value0 == values.value0 && value1 == values.value1 && value2 == values.value2 && value3 == values.value3;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value0, value1, value2, value3);
        }
    }
}
