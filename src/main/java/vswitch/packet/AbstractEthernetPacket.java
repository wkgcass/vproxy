package vswitch.packet;

import vswitch.util.MacAddress;

public abstract class AbstractEthernetPacket extends AbstractPacket {
    public abstract MacAddress getSrc();

    public abstract MacAddress getDst();

    public abstract AbstractPacket getPacket();
}
