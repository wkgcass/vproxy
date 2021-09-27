package io.vproxy.vfd;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Utils;

import java.util.Objects;

public class MacAddress {
    public final ByteArray bytes;
    private final int value0;
    private final int value1;

    public MacAddress(byte[] bytes) {
        this(ByteArray.from(bytes));
    }

    public MacAddress(ByteArray bytes) {
        this.bytes = bytes.copy().unmodifiable();
        this.value0 = bytes.int32(0);
        this.value1 = bytes.uint16(4);
    }

    public MacAddress(String mac) { // example: 0a:00:27:00:00:00
        if (mac.length() != 17) {
            throw new IllegalArgumentException();
        }
        String[] split = mac.split(":");
        if (split.length != 6) {
            throw new IllegalArgumentException();
        }
        byte[] bytes = Utils.allocateByteArrayInitZero(6);
        for (int i = 0; i < 6; i++) {
            String s = split[i];
            if (s.length() != 2) {
                throw new IllegalArgumentException();
            }
            try {
                bytes[i] = (byte) Integer.parseInt(s, 16);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(e);
            }
        }

        this.bytes = ByteArray.from(bytes).unmodifiable();
        this.value0 = this.bytes.int32(0);
        this.value1 = this.bytes.uint16(4);
    }

    public boolean isBroadcast() {
        for (int i = 0; i < this.bytes.length(); ++i) {
            byte b = this.bytes.get(i);
            if (b != (byte) 0xff) {
                return false;
            }
        }
        return true;
    }

    public boolean isMulticast() {
        return isIpv4Multicast() || isIpv6Multicast();
    }

    public boolean isUnicast() {
        return !isBroadcast() && !isMulticast();
    }

    private boolean isIpv4Multicast() {
        if (bytes.length() < 4) { // we need to check the first 25 bits
            // not a valid mac address, normally should be 6, so not multicast
            return false;
        }
        // 0000 0001 0000 0000 0101 1110 0.......
        if (bytes.get(0) != 0b00000001) {
            return false;
        }
        if (bytes.get(1) != 0b00000000) {
            return false;
        }
        if (bytes.get(2) != 0b01011110) {
            return false;
        }
        //noinspection RedundantIfStatement
        if ((bytes.get(3) & 0b10000000) != 0b00000000) { // first bit 0
            return false;
        }
        return true;
    }

    private boolean isIpv6Multicast() {
        if (bytes.length() < 2) { // we need to check the first 2 bytes
            // not a valid mac address, normally should be 6, so not multicast
            return false;
        }
        return bytes.get(0) == 0x33 && bytes.get(1) == 0x33;
    }

    @Override
    public String toString() {
        String hex = this.bytes.toHexString();
        StringBuilder sb = new StringBuilder();
        char[] chars = hex.toCharArray();
        for (int i = 0; i < chars.length; ++i) {
            if (i != 0 && i % 2 == 0) {
                sb.append(":");
            }
            sb.append(chars[i]);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MacAddress that = (MacAddress) o;
        return value0 == that.value0 && value1 == that.value1;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value0, value1);
    }
}
