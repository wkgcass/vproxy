package vswitch.iface;

import vfd.DatagramFD;
import vproxy.util.Utils;
import vswitch.Switch;
import vswitch.packet.VProxyEncryptedPacket;
import vswitch.packet.VXLanPacket;
import vswitch.util.Consts;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;

public class UserIface implements Iface, ClientSideVniGetterSetter, ServerSideVniGetterSetter {
    public final InetSocketAddress udpSockAddress;
    public final String user;

    private int clientSideVni;
    private int serverSideVni;

    private final Map<String, Switch.UserInfo> userMapRef;

    public UserIface(InetSocketAddress udpSockAddress, String user, Map<String, Switch.UserInfo> userMapRef) {
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
        return "Iface(" + Utils.l4addrStr(udpSockAddress) + ",user:" + user.replace(Consts.USER_PADDING, "") + ')';
    }

    @Override
    public void sendPacket(DatagramFD serverUDPSock, VXLanPacket vxlan, ByteBuffer writeBuf) throws IOException {
        if (vxlan.getVni() != clientSideVni && clientSideVni != 0) {
            vxlan.setVni(clientSideVni);
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
    public int getServerSideVni(int hint) {
        return serverSideVni;
    }

    @Override
    public void setServerSideVni(int serverSideVni) {
        this.serverSideVni = serverSideVni;
    }

    @Override
    public int getClientSideVni() {
        return clientSideVni;
    }

    @Override
    public void setClientSideVni(int clientSideVni) {
        this.clientSideVni = clientSideVni;
    }
}
