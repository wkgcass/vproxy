package vswitch.packet;

import vproxy.util.ByteArray;

import java.util.Objects;

import static vproxy.util.Utils.runAvoidNull;

public class PacketBytes extends AbstractPacket {
    private ByteArray bytes;

    @Override
    public String from(ByteArray bytes) {
        this.bytes = bytes;
        return null;
    }

    @Override
    protected ByteArray buildPacket() {
        return bytes;
    }

    @Override
    public String toString() {
        return "PacketBytes(" + runAvoidNull(() -> bytes.toHexString(), "null") + ')';
    }

    public ByteArray getBytes() {
        return bytes;
    }

    public void setBytes(ByteArray bytes) {
        clearRawPacket();
        this.bytes = bytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PacketBytes that = (PacketBytes) o;
        return Objects.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bytes);
    }
}
