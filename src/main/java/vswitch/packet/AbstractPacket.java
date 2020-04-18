package vswitch.packet;

import vproxy.util.ByteArray;

public abstract class AbstractPacket {
    protected ByteArray raw;

    public abstract String from(ByteArray bytes);

    public ByteArray getRawPacket() {
        return raw;
    }
}
