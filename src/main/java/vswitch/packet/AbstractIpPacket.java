package vswitch.packet;

import java.net.InetAddress;

public abstract class AbstractIpPacket extends AbstractPacket {
    public abstract InetAddress getSrc();

    public abstract InetAddress getDst();

    public abstract AbstractPacket getPacket();
}
