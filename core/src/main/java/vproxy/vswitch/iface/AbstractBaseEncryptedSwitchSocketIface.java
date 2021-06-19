package vproxy.vswitch.iface;

import vproxy.base.util.Consts;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.vfd.IPPort;
import vproxy.vpacket.VProxyEncryptedPacket;
import vproxy.vswitch.SocketBuffer;
import vproxy.vswitch.util.SwitchUtils;
import vproxy.vswitch.util.UserInfo;

import java.io.IOException;
import java.util.Map;

public abstract class AbstractBaseEncryptedSwitchSocketIface extends AbstractBaseSwitchSocketIface implements IfaceCanSendVProxyPacket {
    private final String user;
    private Map<String, UserInfo> users;

    protected AbstractBaseEncryptedSwitchSocketIface(String user, IPPort remote) {
        super(remote);
        this.user = user;
    }

    @Override
    public void init(IfaceInitParams params) throws Exception {
        super.init(params);
        this.users = params.users;
    }

    @Override
    public void sendPacket(SocketBuffer skb) {
        assert Logger.lowLevelDebug(this + ".sendPacket(" + skb + ")");

        var vxlan = SwitchUtils.getOrMakeVXLanPacket(skb);
        // clear all possible flags and counters
        vxlan.setReserved1(0);
        vxlan.setReserved2(0);
        // make packet
        UserInfo uinfo = users.get(user);
        if (uinfo == null) {
            Logger.error(LogType.IMPROPER_USE, "cannot retrieve key for user " + user);
            return; // packet dropped
        }
        var p = new VProxyEncryptedPacket(unused -> uinfo.key);
        p.setVxlan(vxlan);
        p.setMagic(Consts.VPROXY_SWITCH_MAGIC);
        p.setType(Consts.VPROXY_SWITCH_TYPE_VXLAN);

        sendVProxyPacket(p);
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public void sendVProxyPacket(VProxyEncryptedPacket pkt) {
        pkt.setUser(user);

        sndBuf.limit(sndBuf.capacity()).position(0);
        byte[] bytes = pkt.getRawPacket().toJavaArray();
        sndBuf.put(bytes);
        sndBuf.flip();

        try {
            if (sockConnected) {
                sock.write(sndBuf);
            } else {
                sock.send(sndBuf, remote);
            }
        } catch (IOException e) {
            Logger.error(LogType.CONN_ERROR, "sending packet to " + this + " failed", e);
        }
    }
}
