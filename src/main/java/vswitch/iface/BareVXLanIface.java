package vswitch.iface;

import vfd.DatagramFD;
import vproxy.util.Utils;
import vswitch.packet.VXLanPacket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;

public class BareVXLanIface implements Iface, ServerSideVniGetterSetter {
    public final InetSocketAddress udpSockAddress; // for vxlan or vproxy wrapped vxlan
    private int serverSideVni;

    public BareVXLanIface(InetSocketAddress udpSockAddress) {
        this.udpSockAddress = udpSockAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BareVXLanIface that = (BareVXLanIface) o;
        return Objects.equals(udpSockAddress, that.udpSockAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(udpSockAddress);
    }

    @Override
    public String toString() {
        return "Iface(" + Utils.l4addrStr(udpSockAddress) + ')';
    }

    @Override
    public void sendPacket(DatagramFD serverUDPSock, VXLanPacket vxlan, ByteBuffer writeBuf) throws IOException {
        byte[] bytes = vxlan.getRawPacket().toJavaArray();
        writeBuf.put(bytes);
        writeBuf.flip();
        serverUDPSock.send(writeBuf, udpSockAddress);
    }

    @Override
    public void destroy() {
        // do nothing
    }

    @Override
    public int getServerSideVni(int hint) {
        return serverSideVni;
    }

    @Override
    public void setServerSideVni(int vni) {
        this.serverSideVni = vni;
    }
}
