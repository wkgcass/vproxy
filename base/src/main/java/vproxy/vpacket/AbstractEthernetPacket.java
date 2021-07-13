package vproxy.vpacket;

import vproxy.base.util.ByteArray;
import vproxy.vfd.MacAddress;

public abstract class AbstractEthernetPacket extends AbstractPacket {
    public abstract String from(ByteArray bytes, boolean skipIPPacket);

    public abstract MacAddress getSrc();

    public abstract void setSrc(MacAddress src);

    public abstract MacAddress getDst();

    public abstract void setDst(MacAddress dst);

    public abstract AbstractPacket getPacket();

    public abstract ByteArray getPacketBytes();

    public abstract void clearPacketBytes();
}
