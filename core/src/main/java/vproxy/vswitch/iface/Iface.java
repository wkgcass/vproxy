package vproxy.vswitch.iface;

import vproxy.vswitch.PacketBuffer;

public interface Iface {
    void init(IfaceInitParams params) throws Exception;

    void sendPacket(PacketBuffer pkb);

    void destroy();

    PacketBuffer pollPacket(); // nullable

    int getLocalSideVni(int hint);

    int getOverhead();

    int getBaseMTU();

    void setBaseMTU(int baseMTU);

    boolean isFloodAllowed();

    void setFloodAllowed(boolean floodAllowed);

    String paramsToString();
}
