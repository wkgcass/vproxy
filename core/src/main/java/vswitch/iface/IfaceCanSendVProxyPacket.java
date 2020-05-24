package vswitch.iface;

import vfd.DatagramFD;
import vpacket.VProxyEncryptedPacket;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface IfaceCanSendVProxyPacket {
    void sendVProxyPacket(DatagramFD serverUDPSock, VProxyEncryptedPacket p, ByteBuffer writeBuf) throws IOException;
}
