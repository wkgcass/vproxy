package vswitch.iface;

import vfd.DatagramFD;
import vfd.IPPort;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.LogType;
import vproxy.util.Logger;
import vswitch.packet.VProxyEncryptedPacket;
import vswitch.packet.VXLanPacket;
import vswitch.util.Consts;
import vswitch.util.UserInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class UserClientIface implements Iface, IfaceCanSendVProxyPacket {
    public final UserInfo user;
    public final DatagramFD sock;
    public final IPPort remoteAddress;

    private SelectorEventLoop bondLoop;

    private boolean connected = false;

    public UserClientIface(UserInfo user, DatagramFD sock, IPPort remoteAddress) {
        this.user = user;
        this.sock = sock;
        this.remoteAddress = remoteAddress;
    }

    public void detachedFromLoopAlert() {
        bondLoop = null;
    }

    public void attachedToLoopAlert(SelectorEventLoop newLoop) {
        this.bondLoop = newLoop;
    }

    public void setConnected(boolean connected) {
        boolean oldConnected = this.connected;
        this.connected = connected;
        if (connected) {
            if (!oldConnected) {
                Logger.alert("connected to switch: " + this);
            }
        } else {
            if (oldConnected) {
                Logger.warn(LogType.ALERT, "lost connection to switch: " + this);
            }
        }
    }

    public boolean isConnected() {
        return connected;
    }

    @Override
    public String toString() {
        return "Iface(ucli:" + user.user.replace(Consts.USER_PADDING, "") + "," + remoteAddress.formatToIPPortString() + ",vni:" + user.vni
            + ")" + (connected ? "[UP]" : "[DOWN]");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserClientIface iface = (UserClientIface) o;
        return Objects.equals(user, iface.user) &&
            Objects.equals(remoteAddress, iface.remoteAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, remoteAddress);
    }

    @Override
    public void sendPacket(DatagramFD serverUDPSock, VXLanPacket vxlan, ByteBuffer writeBuf) throws IOException {
        if (bondLoop == null) {
            assert Logger.lowLevelDebug("bond loop is null, do not send data via this iface for now");
            return;
        }
        if (!connected) {
            assert Logger.lowLevelDebug("not connected yet, do not send data via this iface for now");
            return;
        }

        vxlan.setVni(user.vni);
        VProxyEncryptedPacket p = new VProxyEncryptedPacket(u -> user.key);
        p.setMagic(Consts.VPROXY_SWITCH_MAGIC);
        p.setType(Consts.VPROXY_SWITCH_TYPE_VXLAN);
        p.setVxlan(vxlan);

        sendVProxyPacket(null, p, writeBuf);
    }

    @Override
    public void sendVProxyPacket(DatagramFD notUsed, VProxyEncryptedPacket p, ByteBuffer writeBuf) throws IOException {
        if (bondLoop == null) {
            assert Logger.lowLevelDebug("bond loop is null, do not send data via this iface for now");
            return;
        }

        p.setUser(user.user);

        byte[] bytes = p.getRawPacket().toJavaArray();
        writeBuf.put(bytes);
        writeBuf.flip();
        sock.write(writeBuf);
    }

    @Override
    public void destroy() {
        if (bondLoop != null) {
            try {
                bondLoop.remove(sock);
            } catch (Throwable ignore) {
            }
            bondLoop = null;
        }
        try {
            sock.close();
        } catch (IOException e) {
            Logger.shouldNotHappen("close udp sock " + sock + " failed", e);
        }
    }

    @Override
    public int getLocalSideVni(int hint) {
        return user.vni;
    }
}
