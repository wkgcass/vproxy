package vproxy.vpacket;

import vproxy.base.util.ByteArray;
import vproxy.vfd.IP;

public abstract class AbstractIpPacket extends AbstractPacket {
    public abstract String readIPProto(ByteArray bytes);

    public abstract IP getSrc();

    public abstract IP getDst();

    public abstract AbstractPacket getPacket();

    public abstract int getHopLimit();

    public abstract void setHopLimit(int n);

    public abstract int getProtocol();

    public abstract void setPacket(AbstractPacket packet);
}
