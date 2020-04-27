package vswitch.iface;

import vfd.DatagramFD;
import vproxy.util.Utils;
import vswitch.packet.VXLanPacket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;

public class RemoteSwitchIface implements Iface {
    public final String alias;
    public final InetSocketAddress udpSockAddress;

    public RemoteSwitchIface(String alias, InetSocketAddress udpSockAddress) {
        this.alias = alias;
        this.udpSockAddress = udpSockAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RemoteSwitchIface that = (RemoteSwitchIface) o;
        return Objects.equals(alias, that.alias) &&
            Objects.equals(udpSockAddress, that.udpSockAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alias, udpSockAddress);
    }

    @Override
    public String toString() {
        return "Iface(remote:" + alias + "," + Utils.l4addrStr(udpSockAddress) + ")";
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
        return hint;
    }
}
