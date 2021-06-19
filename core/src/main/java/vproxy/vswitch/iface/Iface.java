package vproxy.vswitch.iface;

import vproxy.vswitch.SocketBuffer;

public interface Iface {
    void init(IfaceInitParams params) throws Exception;

    void sendPacket(SocketBuffer skb);

    void destroy();

    SocketBuffer pollPacket(); // nullable

    int getLocalSideVni(int hint);

    int getOverhead();

    int getBaseMTU();

    void setBaseMTU(int baseMTU);

    boolean isFloodAllowed();

    void setFloodAllowed(boolean floodAllowed);

    String paramsToString();
}
