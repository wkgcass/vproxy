package io.vproxy.vswitch.iface;

import io.vproxy.vpacket.VProxyEncryptedPacket;

public interface IfaceCanSendVProxyPacket {
    void sendVProxyPacket(VProxyEncryptedPacket pkt);
}
