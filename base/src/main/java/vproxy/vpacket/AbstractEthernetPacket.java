package vproxy.vpacket;

import vproxy.vfd.MacAddress;

public abstract class AbstractEthernetPacket extends AbstractPacket {
    protected PacketDataBuffer packetBytes;

    public abstract String from(PacketDataBuffer raw, boolean allowPartial);

    public abstract MacAddress getSrc();

    public abstract void setSrc(MacAddress src);

    public abstract MacAddress getDst();

    public abstract void setDst(MacAddress dst);

    public abstract int getType();

    public abstract AbstractPacket getPacket();

    public PacketDataBuffer getPacketBytes() {
        return packetBytes;
    }

    @Override
    public void clearAllRawPackets() {
        clearRawPacket();
        getPacket().clearAllRawPackets();
    }

    public void clearPacketBytes() {
        this.packetBytes = null;
    }

    @Override
    public abstract AbstractEthernetPacket copy();
}
