package io.vproxy.vpacket;

public abstract class TransportPacket extends AbstractPacket implements PartialPacket {
    public abstract int getSrcPort();

    public abstract void setSrcPort(int srcPort);

    public abstract int getDstPort();

    public abstract void setDstPort(int dstPort);

    @Override
    public abstract TransportPacket copy();
}
