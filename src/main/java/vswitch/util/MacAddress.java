package vswitch.util;

import vproxy.util.ByteArray;

import java.util.Objects;

public class MacAddress {
    public final ByteArray bytes;
    private final int hashCode;

    public MacAddress(ByteArray bytes) {
        this.bytes = bytes.copy();
        this.hashCode = Objects.hashCode(bytes);
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
        return Objects.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
