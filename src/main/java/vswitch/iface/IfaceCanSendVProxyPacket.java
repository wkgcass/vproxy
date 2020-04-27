package vswitch.iface;

import vfd.DatagramFD;
import vswitch.packet.VProxyEncryptedPacket;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface IfaceCanSendVProxyPacket {
    void sendVProxyPacket(DatagramFD serverUDPSock, VProxyEncryptedPacket p, ByteBuffer writeBuf) throws IOException;
}
