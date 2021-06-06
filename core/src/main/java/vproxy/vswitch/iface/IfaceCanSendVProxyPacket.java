package vproxy.vswitch.iface;

import vproxy.vfd.DatagramFD;
import vproxy.vpacket.VProxyEncryptedPacket;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface IfaceCanSendVProxyPacket {
    void sendVProxyPacket(DatagramFD serverUDPSock, VProxyEncryptedPacket p, ByteBuffer writeBuf) throws IOException;
}
