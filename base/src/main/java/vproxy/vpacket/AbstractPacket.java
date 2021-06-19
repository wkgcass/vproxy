package vproxy.vpacket;

import vproxy.base.util.ByteArray;

public abstract class AbstractPacket {
    protected ByteArray raw;
    private AbstractPacket parentPacket;

    public abstract String from(ByteArray bytes);

    public final ByteArray getRawPacket() {
        if (raw == null) {
            raw = buildPacket();
        }
        return raw;
    }

    public final void clearRawPacket() {
        raw = null;
        if (parentPacket != null) {
            parentPacket.clearRawPacket();
        }
    }

    protected abstract ByteArray buildPacket();

    protected final void recordParent(AbstractPacket parentPacket) {
        this.parentPacket = parentPacket;
    }

    public abstract String description();
}
