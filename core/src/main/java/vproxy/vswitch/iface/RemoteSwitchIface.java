package vproxy.vswitch.iface;

import vproxy.base.util.Consts;
import vproxy.vfd.IPPort;
import vproxy.vswitch.SocketBuffer;

import java.util.Objects;

public class RemoteSwitchIface extends AbstractBaseSwitchSocketIface implements Iface {
    public final String alias;
    public final IPPort udpSockAddress;
    public final boolean addSwitchFlag;

    public RemoteSwitchIface(String alias, IPPort udpSockAddress, boolean addSwitchFlag) {
        super(udpSockAddress);
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
    public void sendPacket(SocketBuffer skb) {
        super.sendPacket(skb);
    }

    @Override
    protected void manipulate() {
        if (addSwitchFlag) {
            sndBuf.put(1, (byte) (sndBuf.get(1) | ((Consts.I_AM_FROM_SWITCH >> 16) & 0xff)));
        } else {
            // remove all possible flags or counters
            sndBuf.put(1, (byte) 0);
            sndBuf.put(2, (byte) 0);
            sndBuf.put(3, (byte) 0);
            sndBuf.put(7, (byte) 0);
        }
    }

    @Override
    public void destroy() {
        // do nothing
    }

    @Override
    public int getLocalSideVni(int hint) {
        return hint;
    }

    @Override
    public int getOverhead() {
        return 14 /* inner ethernet */ + 8 /* vxlan header */ + 8 /* udp header */ + 40 /* ipv6 header common */;
    }
}
