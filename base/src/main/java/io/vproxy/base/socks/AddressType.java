package io.vproxy.base.socks;

public enum AddressType {
    ipv4((byte) 0x01),
    domain((byte) 0x03),
    ipv6((byte) 0x04),
    ;
    public final byte code;

    AddressType(byte code) {
        this.code = code;
    }
}
