package vswitch.iface;

import vfd.DatagramFD;
import vfd.IPPort;
import vswitch.packet.VXLanPacket;
import vswitch.util.Consts;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class RemoteSwitchIface implements Iface {
    public final String alias;
    public final IPPort udpSockAddress;
    public final boolean addSwitchFlag;

    public RemoteSwitchIface(String alias, IPPort udpSockAddress, boolean addSwitchFlag) {
        this.alias = alias;
        this.udpSockAddress = udpSockAddress;
        this.addSwitchFlag = addSwitchFlag;
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
        return "Iface(remote:" + alias + "," + udpSockAddress.formatToIPPortString() + ")";
    }

    @Override
    public void sendPacket(DatagramFD serverUDPSock, VXLanPacket vxlan, ByteBuffer writeBuf) throws IOException {
        byte[] bytes = vxlan.getRawPacket().toJavaArray();
        writeBuf.put(bytes);
        writeBuf.flip();
        if (addSwitchFlag) {
            writeBuf.put(1, (byte) (bytes[1] | ((Consts.I_AM_FROM_SWITCH >> 16) & 0xff)));
        } else {
            // remove all possible flags or counters
            writeBuf.put(1, (byte) 0);
            writeBuf.put(2, (byte) 0);
            writeBuf.put(3, (byte) 0);
            writeBuf.put(7, (byte) 0);
        }
        serverUDPSock.send(writeBuf, udpSockAddress);
    }

    @Override
    public void destroy() {
        // do nothing
    }

    @Override
    public int getLocalSideVni(int hint) {
        return hint;
    }
}
