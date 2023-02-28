package io.vproxy.vpacket.dhcp;

import io.vproxy.base.util.ByteArray;

public class DHCPOption {
    public byte type;
    public int len;
    protected ByteArray content;

    public ByteArray serialize() {
        if (content == null) {
            content = ByteArray.allocate(len);
        }
        return ByteArray.allocate(2)
            .set(0, type).set(1, (byte) len)
            .concat(content);
    }

    public int deserialize(ByteArray arr) throws Exception {
        if (arr.length() < 1) {
            throw new Exception("input too short for dhcp option: cannot read type");
        }
        type = arr.get(0);
        if (arr.length() < 2) {
            throw new Exception("input too short for dhcp option: cannot read len");
        }
        len = arr.uint8(1);
        if (arr.length() < 2 + len) {
            throw new Exception("input too short for dhcp option content: requiring" + len);
        }
        content = arr.sub(2, len);
        return 2 + len;
    }

    @Override
    public String toString() {
        return "DHCPOption{" +
            "type=" + type +
            ", len=" + len +
            ", content=" + (content == null ? "null" : content.toHexString()) +
            '}';
    }
}
