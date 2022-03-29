package io.vproxy.vpacket;

import io.vproxy.vfd.IPPort;

public abstract class TransportPacket extends AbstractPacket implements PartialPacket {
    public abstract int getSrcPort();

    public abstract void setSrcPort(int srcPort);

    public abstract int getDstPort();

    public abstract void setDstPort(int dstPort);

    public IPPort getSrc(AbstractIpPacket ip) {
        return new IPPort(ip.getSrc(), getSrcPort());
    }

    public IPPort getDst(AbstractIpPacket ip) {
        return new IPPort(ip.getDst(), getDstPort());
    }

    @Override
    public abstract TransportPacket copy();
}
