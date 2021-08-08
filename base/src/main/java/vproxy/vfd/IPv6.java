package vproxy.vfd;

import vproxy.base.util.ByteArray;

import java.net.Inet6Address;
import java.util.Arrays;

public class IPv6 extends IP {
    IPv6(byte[] bytes) {
        super(ByteArray.from(bytes));
    }

    @Override
    public Inet6Address toInetAddress() {
        return (Inet6Address) super.toInetAddress();
    }

    @Override
    public boolean equals(Object that) {
        if (that == null) return false;
        if (!(that instanceof IPv6)) return false;
        return Arrays.equals(getAddress(), ((IPv6) that).getAddress());
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
