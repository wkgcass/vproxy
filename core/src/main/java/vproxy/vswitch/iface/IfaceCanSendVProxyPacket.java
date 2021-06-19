package vproxy.vswitch.iface;

import vproxy.vpacket.VProxyEncryptedPacket;
import vproxy.vswitch.SocketBuffer;

public interface IfaceCanSendVProxyPacket {
    void sendVProxyPacket(VProxyEncryptedPacket pkt);
}
