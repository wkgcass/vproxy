package vpacket;

import vfd.MacAddress;

public abstract class AbstractEthernetPacket extends AbstractPacket {
    public abstract MacAddress getSrc();

    public abstract void setSrc(MacAddress src);

    public abstract MacAddress getDst();

    public abstract void setDst(MacAddress dst);

    public abstract AbstractPacket getPacket();
}
