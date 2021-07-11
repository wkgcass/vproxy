package vproxy.vpacket;

import vproxy.base.util.ByteArray;

public abstract class AbstractPacket {
    protected ByteArray raw;
    private AbstractPacket parentPacket;
    protected boolean requireUpdatingChecksum = false;

    public abstract String from(ByteArray bytes);

    public final ByteArray getRawPacket() {
        if (raw == null) {
            raw = buildPacket();
        } else {
            checkAndUpdateChecksum();
        }
        return raw;
    }

    protected final void checkAndUpdateChecksum() {
        if (requireUpdatingChecksum) {
            updateChecksum();
        }
    }

    public final void clearRawPacket() {
        raw = null;
        if (parentPacket != null) {
            parentPacket.clearRawPacket();
        }
    }

    public final void clearChecksum() {
        requireUpdatingChecksum = true;
        if (parentPacket != null) {
            parentPacket.clearChecksum();
        }
    }

    protected abstract ByteArray buildPacket();

    protected abstract void updateChecksum();

    protected final void recordParent(AbstractPacket parentPacket) {
        this.parentPacket = parentPacket;
    }

    public abstract String description();
}
