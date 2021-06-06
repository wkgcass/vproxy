package vproxy.vpacket;

import vproxy.base.util.ByteArray;

public abstract class AbstractPacket {
    protected ByteArray raw;

    public abstract String from(ByteArray bytes);

    public final ByteArray getRawPacket() {
        if (raw == null) {
            raw = buildPacket();
        }
        return raw;
    }

    public final void clearRawPacket() {
        raw = null;
    }

    protected abstract ByteArray buildPacket();
}
