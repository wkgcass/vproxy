package vswitch.iface;

import vfd.DatagramFD;
import vswitch.packet.VXLanPacket;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface Iface {
    void sendPacket(DatagramFD serverUDPSock, VXLanPacket vxlan, ByteBuffer writeBuf) throws IOException;

    void destroy();

    int getServerSideVni(int hint);
}
