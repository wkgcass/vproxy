package vswitch.iface;

import vfd.DatagramFD;
import vfd.IPPort;
import vpacket.VXLanPacket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class BareVXLanIface implements Iface, LocalSideVniGetterSetter {
    public final IPPort udpSockAddress; // for vxlan or vproxy wrapped vxlan
    private int serverSideVni;

    public BareVXLanIface(IPPort udpSockAddress) {
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
        return "Iface(" + udpSockAddress.formatToIPPortString() + ')';
    }

    @Override
    public void sendPacket(DatagramFD serverUDPSock, VXLanPacket vxlan, ByteBuffer writeBuf) throws IOException {
        byte[] bytes = vxlan.getRawPacket().toJavaArray();

        writeBuf.put(bytes);
        writeBuf.flip();

        // keep reserved fields empty
        writeBuf.put(1, (byte) 0);
        writeBuf.put(2, (byte) 0);
        writeBuf.put(3, (byte) 0);
        writeBuf.put(7, (byte) 0);
        serverUDPSock.send(writeBuf, udpSockAddress);
    }

    @Override
    public void destroy() {
        // do nothing
    }

    @Override
    public int getLocalSideVni(int hint) {
        return serverSideVni;
    }

    @Override
    public void setLocalSideVni(int vni) {
        this.serverSideVni = vni;
    }
}
