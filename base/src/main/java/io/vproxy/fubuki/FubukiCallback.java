package io.vproxy.fubuki;

import io.vproxy.base.util.ByteArray;
import io.vproxy.vfd.IPv4;

public interface FubukiCallback {
    void onPacket(Fubuki fubuki, ByteArray packet);

    void addAddress(Fubuki fubuki, IPv4 ip, IPv4 mask);

    void deleteAddress(Fubuki fubuki, IPv4 ip, IPv4 mask);
}
