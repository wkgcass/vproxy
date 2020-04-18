package vswitch.packet;

import vproxy.util.ByteArray;

import static vproxy.util.Utils.runAvoidNull;

public class PacketBytes extends AbstractPacket {
    public ByteArray bytes;

    @Override
    public String from(ByteArray bytes) {
        this.bytes = bytes;
        return null;
    }

    @Override
    public String toString() {
        return "PacketBytes(" + runAvoidNull(() -> bytes.toHexString(), "null") + ')';
    }
}
