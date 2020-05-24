package vpacket;

import vfd.IP;

public abstract class AbstractIpPacket extends AbstractPacket {
    public abstract IP getSrc();

    public abstract IP getDst();

    public abstract AbstractPacket getPacket();

    public abstract int getHopLimit();

    public abstract void setHopLimit(int n);

    public abstract int getProtocol();

    public abstract void setPacket(AbstractPacket packet);
}
