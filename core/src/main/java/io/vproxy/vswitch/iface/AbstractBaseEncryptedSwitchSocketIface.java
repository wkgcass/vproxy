package io.vproxy.vswitch.iface;

import io.vproxy.base.util.Consts;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.crypto.Aes256Key;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.VProxyEncryptedPacket;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.crypto.Aes256Key;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.VProxyEncryptedPacket;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.util.SwitchUtils;

import java.io.IOException;

public abstract class AbstractBaseEncryptedSwitchSocketIface extends AbstractBaseSwitchSocketIface implements IfaceCanSendVProxyPacket {
    private final String user;

    protected AbstractBaseEncryptedSwitchSocketIface(String user, IPPort remote) {
        super(remote);
        this.user = user;
    }

    @Override
    public void sendPacket(PacketBuffer pkb) {
        assert Logger.lowLevelDebug(this + ".sendPacket(" + pkb + ")");

        var vxlan = SwitchUtils.getOrMakeVXLanPacket(pkb);
        // clear all possible flags and counters
        vxlan.setReserved1(0);
        vxlan.setReserved2(0);
        // make packet
        Aes256Key key = getEncryptionKey();
        if (key == null) {
            Logger.error(LogType.IMPROPER_USE, "cannot retrieve key for user " + user);
            return; // packet dropped
        }
        var p = new VProxyEncryptedPacket(key);
        p.setVxlan(vxlan);
        p.setMagic(Consts.VPROXY_SWITCH_MAGIC);
        p.setType(Consts.VPROXY_SWITCH_TYPE_VXLAN);

        sendVProxyPacket(p);
    }

    abstract protected Aes256Key getEncryptionKey();

    @SuppressWarnings("DuplicatedCode")
    @Override
    public void sendVProxyPacket(VProxyEncryptedPacket pkt) {
        pkt.setUser(user);

        sndBuf.limit(sndBuf.capacity()).position(0);
        byte[] bytes = pkt.getRawPacket(0).toJavaArray();
        sndBuf.put(bytes);
        sndBuf.flip();

        statistics.incrTxPkts();
        statistics.incrTxBytes(sndBuf.limit() - sndBuf.position());

        try {
            if (sockConnected) {
                sock.write(sndBuf);
            } else {
                sock.send(sndBuf, remote);
            }
        } catch (IOException e) {
            assert Logger.lowLevelDebug("sending packet to " + this + " failed: " + e);
        }
    }
}
