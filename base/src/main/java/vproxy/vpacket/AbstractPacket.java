package vproxy.vpacket;

import vproxy.base.util.ByteArray;
import vproxy.base.util.Logger;

public abstract class AbstractPacket {
    protected PacketDataBuffer raw;
    private AbstractPacket parentPacket;
    protected boolean requireUpdatingChecksum = false;

    public abstract String from(PacketDataBuffer raw);

    public final ByteArray getRawPacket() {
        if (raw == null) {
            raw = new PacketDataBuffer(buildPacket());
        } else {
            checkAndUpdateChecksum();
        }
        return raw.pktBuf;
    }

    protected final void checkAndUpdateChecksum() {
        if (requireUpdatingChecksum) {
            updateChecksum();
        }
    }

    public final void clearRawPacket() {
        if (raw != null) {
            raw.clearBuffers();
        }
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

    protected final boolean consumeHeadroomAndMove(int room, int moveLen) {
        if (raw == null) {
            assert Logger.lowLevelDebug("requireMoreHeadroom return false because no raw array");
            return false;
        }
        if (raw.pktOff < room) {
            assert Logger.lowLevelDebug("requireMoreHeadroom return false because pktOff " + raw.pktOff + " < room " + room);
            return false;
        }
        raw.pktBuf = raw.fullbuf.sub(raw.pktOff - room, raw.pktBuf.length() + room);
        for (int i = 0; i < moveLen; ++i) {
            raw.pktBuf.set(i, raw.pktBuf.get(room + i));
        }
        return true;
    }

    protected final void returnHeadroomAndMove(int room, int moveLen) {
        if (raw == null) {
            return; // nothing to move
        }
        if (raw.pktBuf.length() - room < moveLen) {
            throw new ArrayIndexOutOfBoundsException("pktBuf.length " + raw.pktBuf.length() + " - room " + room + " < moveLen " + moveLen);
        }
        for (int i = moveLen - 1; i >= 0; --i) {
            raw.pktBuf.set(room + i, raw.pktBuf.get(i));
        }
        raw.pktBuf = raw.pktBuf.sub(room, raw.pktBuf.length() - room);
    }

    protected final void setPktBufLen(PacketDataBuffer raw, int len) {
        if (raw == null) {
            raw = this.raw;
        }
        if (raw == null) {
            return;
        }
        raw.pad = raw.fullbuf.length() - raw.pktOff - len;
        raw.pktBuf = raw.fullbuf.sub(raw.pktOff, len);
    }
}
