package vfd;

import java.net.Inet4Address;
import java.util.Arrays;

public class IPv4 extends IP {
    IPv4(byte[] bytes) {
        super(bytes);
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
}
