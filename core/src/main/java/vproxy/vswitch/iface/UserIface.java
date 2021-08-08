package vproxy.vswitch.iface;

import vproxy.base.util.Consts;
import vproxy.base.util.Logger;
import vproxy.base.util.crypto.Aes256Key;
import vproxy.vfd.IPPort;
import vproxy.vswitch.PacketBuffer;
import vproxy.vswitch.util.UserInfo;

import java.util.Map;
import java.util.Objects;

public class UserIface extends AbstractBaseEncryptedSwitchSocketIface implements RemoteSideVniGetterSetter, LocalSideVniGetterSetter {
    public final IPPort udpSockAddress;
    public final String user;
    private Map<String, UserInfo> users;

    private int remoteSideVni;
    private int localSideVni;

    public UserIface(IPPort udpSockAddress, String user) {
        super(user, udpSockAddress);
        this.udpSockAddress = udpSockAddress;
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserIface userIface = (UserIface) o;
        return Objects.equals(user, userIface.user) &&
            Objects.equals(udpSockAddress, userIface.udpSockAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, udpSockAddress);
    }

    @Override
    public String name() {
        return "user:" + user.replace(Consts.USER_PADDING, "");
    }

    @Override
    public String toString() {
        return "Iface(user:" + user.replace(Consts.USER_PADDING, "") + "," + udpSockAddress.formatToIPPortString() + ",lvni:" + localSideVni + ",rvni:" + remoteSideVni + ')';
    }

    @Override
    public void init(IfaceInitParams params) throws Exception {
        super.init(params);
        this.users = params.users;
    }

    @Override
    protected Aes256Key getEncryptionKey() {
        UserInfo uinfo = users.get(user);
        if (uinfo == null) {
            return null;
        }
        return uinfo.key;
    }

    @Override
    public void sendPacket(PacketBuffer pkb) {
        // should set the remote side vni to reduce the chance of info leak on server side
        if (remoteSideVni == 0) {
            assert Logger.lowLevelDebug("remote side vni not learnt yet, drop the packet for now");
            return;
        }
        pkb.executeWithVni(remoteSideVni, () -> super.sendPacket(pkb));
    }

    @Override
    public void destroy() {
    }

    @Override
    public int getLocalSideVni(int hint) {
        return localSideVni;
    }

    @Override
    public int getOverhead() {
        return 28 /* encryption header */ + 14 /* inner ethernet */ + 8 /* vxlan header */ + 8 /* udp header */ + 40 /* ipv6 header common */;
    }

    @Override
    public void setLocalSideVni(int serverSideVni) {
        this.localSideVni = serverSideVni;
    }

    @Override
    public int getRemoteSideVni() {
        return remoteSideVni;
    }

    @Override
    public void setRemoteSideVni(int remoteSideVni) {
        this.remoteSideVni = remoteSideVni;
    }
}
