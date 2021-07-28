package vproxy.vswitch.iface;

import vproxy.vfd.IPPort;
import vproxy.vswitch.PacketBuffer;

import java.util.Objects;

public class BareVXLanIface extends AbstractBaseSwitchSocketIface implements LocalSideVniGetterSetter {
    public final IPPort udpSockAddress; // remote vxlan address
    private int localSideVni;

    public BareVXLanIface(IPPort udpSockAddress) {
        super(udpSockAddress);
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
    public void sendPacket(PacketBuffer pkb) {
        super.sendPacket(pkb);
    }

    @Override
    protected void manipulate() {
        // keep reserved fields empty
        sndBuf.put(1, (byte) 0);
        sndBuf.put(2, (byte) 0);
        sndBuf.put(3, (byte) 0);
        sndBuf.put(7, (byte) 0);
    }

    @Override
    public void destroy() {
        // do nothing
    }

    @Override
    public int getLocalSideVni(int hint) {
        return localSideVni;
    }

    @Override
    public int getOverhead() {
        return 14 /* inner ethernet */ + 8 /* vxlan header */ + 8 /* udp header */ + 40 /* ipv6 header common */;
    }

    @Override
    public void setLocalSideVni(int vni) {
        this.localSideVni = vni;
    }
}
