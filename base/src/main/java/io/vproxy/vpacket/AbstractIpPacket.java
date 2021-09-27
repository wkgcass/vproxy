package io.vproxy.vpacket;

import io.vproxy.vfd.IP;

public abstract class AbstractIpPacket extends AbstractPacket implements PartialPacket {
    public abstract IP getSrc();

    public abstract IP getDst();

    public abstract AbstractPacket getPacket();

    public abstract int getHopLimit();

    public abstract void setHopLimit(int n);

    public abstract int getProtocol();

    public abstract void setPacket(AbstractPacket packet);

    public abstract void setPacket(int protocol, AbstractPacket packet);

    @Override
    public void clearAllRawPackets() {
        clearRawPacket();
        getPacket().clearAllRawPackets();
    }

    @Override
    public abstract AbstractIpPacket copy();
}
