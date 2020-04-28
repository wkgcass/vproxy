package vswitch.iface;

import vfd.DatagramFD;
import vproxy.util.Logger;
import vproxy.util.Utils;
import vswitch.util.UserInfo;
import vswitch.packet.VProxyEncryptedPacket;
import vswitch.packet.VXLanPacket;
import vswitch.util.Consts;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;

public class UserIface implements Iface, RemoteSideVniGetterSetter, LocalSideVniGetterSetter, IfaceCanSendVProxyPacket {
    public final InetSocketAddress udpSockAddress;
    public final String user;

    private int remoteSideVni;
    private int localSideVni;

    private final Map<String, UserInfo> userMapRef;

    public UserIface(InetSocketAddress udpSockAddress, String user, Map<String, UserInfo> userMapRef) {
        this.udpSockAddress = udpSockAddress;
        this.user = user;
        this.userMapRef = userMapRef;
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
    public String toString() {
        return "Iface(user:" + user.replace(Consts.USER_PADDING, "") + "," + Utils.l4addrStr(udpSockAddress) + ",lvni:" + localSideVni + ",rvni:" + remoteSideVni + ')';
    }

    @Override
    public void sendPacket(DatagramFD serverUDPSock, VXLanPacket vxlan, ByteBuffer writeBuf) throws IOException {
        // should set the remote side vni to reduce the chance of info leak on server side
        if (remoteSideVni == 0) {
            assert Logger.lowLevelDebug("remote side vni not learnt yet, drop the packet for now");
            return;
        }
        if (vxlan.getVni() != remoteSideVni) {
            vxlan.setVni(remoteSideVni);
        }
        VProxyEncryptedPacket p = new VProxyEncryptedPacket(u -> {
            var info = userMapRef.get(user);
            if (info == null) return null;
            return info.key;
        });
        p.setMagic(Consts.VPROXY_SWITCH_MAGIC);
        p.setType(Consts.VPROXY_SWITCH_TYPE_VXLAN);
        p.setVxlan(vxlan);

        sendVProxyPacket(serverUDPSock, p, writeBuf);
    }

    @Override
    public void sendVProxyPacket(DatagramFD serverUDPSock, VProxyEncryptedPacket p, ByteBuffer writeBuf) throws IOException {
        p.setUser(user);

        byte[] bytes = p.getRawPacket().toJavaArray();
        writeBuf.put(bytes);
        writeBuf.flip();
        serverUDPSock.send(writeBuf, udpSockAddress);
    }

    @Override
    public void destroy() {
    }

    @Override
    public int getLocalSideVni(int hint) {
        return localSideVni;
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
