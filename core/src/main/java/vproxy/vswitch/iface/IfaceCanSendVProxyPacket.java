package vproxy.vswitch.iface;

import vproxy.vpacket.VProxyEncryptedPacket;

public interface IfaceCanSendVProxyPacket {
    void sendVProxyPacket(VProxyEncryptedPacket pkt);
}
