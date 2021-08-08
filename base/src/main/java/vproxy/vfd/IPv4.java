package vproxy.vfd;

import vproxy.base.util.ByteArray;

import java.net.Inet4Address;
import java.util.Arrays;

public class IPv4 extends IP {
    IPv4(byte[] bytes) {
        super(ByteArray.from(bytes));
    }

    @Override
    public Inet4Address toInetAddress() {
        return (Inet4Address) super.toInetAddress();
    }

    @Override
    public boolean equals(Object that) {
        if (that == null) return false;
        if (!(that instanceof IPv4)) return false;
        return Arrays.equals(getAddress(), ((IPv4) that).getAddress());
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
