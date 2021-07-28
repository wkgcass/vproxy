package vproxy.vpacket;

import vproxy.vfd.MacAddress;

public abstract class AbstractEthernetPacket extends AbstractPacket {
    protected PacketDataBuffer packetBytes;

    public abstract String from(PacketDataBuffer raw, boolean skipIPPacket);

    public abstract MacAddress getSrc();

    public abstract void setSrc(MacAddress src);

    public abstract MacAddress getDst();

    public abstract void setDst(MacAddress dst);

    public abstract AbstractPacket getPacket();

    public PacketDataBuffer getPacketBytes() {
        return packetBytes;
    }

    public void clearPacketBytes() {
        this.packetBytes = null;
    }
}
