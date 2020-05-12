package vfd;

import java.net.Inet4Address;

public class IPv4 extends IP {
    IPv4(byte[] bytes) {
        super(bytes);
    }

    @Override
    public Inet4Address toInetAddress() {
        return (Inet4Address) super.toInetAddress();
    }
}
