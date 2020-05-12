package vfd;

import java.net.Inet6Address;

public class IPv6 extends IP {
    IPv6(byte[] bytes) {
        super(bytes);
    }

    @Override
    public Inet6Address toInetAddress() {
        return (Inet6Address) super.toInetAddress();
    }
}
