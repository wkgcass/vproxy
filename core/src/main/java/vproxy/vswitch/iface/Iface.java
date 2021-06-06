package vproxy.vswitch.iface;

import vproxy.vfd.DatagramFD;
import vproxy.vpacket.VXLanPacket;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface Iface {
    void sendPacket(DatagramFD serverUDPSock, VXLanPacket vxlan, ByteBuffer writeBuf) throws IOException;

    void destroy();

    int getLocalSideVni(int hint);
}
