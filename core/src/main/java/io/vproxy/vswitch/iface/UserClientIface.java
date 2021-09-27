package io.vproxy.vswitch.iface;

import io.vproxy.base.selector.Handler;
import io.vproxy.base.selector.HandlerContext;
import io.vproxy.base.selector.PeriodicEvent;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.*;
import io.vproxy.base.util.crypto.Aes256Key;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.vfd.DatagramFD;
import io.vproxy.vfd.EventSet;
import io.vproxy.vfd.FDProvider;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.PacketDataBuffer;
import io.vproxy.vpacket.VProxyEncryptedPacket;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.util.SwitchUtils;
import io.vproxy.vswitch.util.UserInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class UserClientIface extends AbstractBaseEncryptedSwitchSocketIface implements IfaceCanSendVProxyPacket {
    public final UserInfo user;
    private final Aes256Key key;

    private SelectorEventLoop bondLoop;

    private boolean connected = false;

    public UserClientIface(UserInfo user, Aes256Key key, IPPort remoteAddress) {
        super(user.user, remoteAddress);
        this.user = user;
        this.key = key;
    }

    public void detachedFromLoopAlert() {
        bondLoop = null;
    }

    public void attachedToLoopAlert(SelectorEventLoop newLoop) {
        this.bondLoop = newLoop;
        try {
            bondLoop.add(sock, EventSet.read(), null, new UserClientHandler(bondLoop));
        } catch (IOException e) {
            Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "adding sock " + sock + " for " + this + " into event loop failed", e);
            callback.alertDeviceDown(this);
        }
    }

    private void setConnected(boolean connected) {
        boolean oldConnected = this.connected;
        this.connected = connected;
        if (connected) {
            if (!oldConnected) {
                Logger.alert("connected to switch: " + this.name());
            }
        } else {
            if (oldConnected) {
                Logger.warn(LogType.ALERT, "lost connection to switch: " + this.name());
            }
        }
    }

    public boolean isConnected() {
        return connected;
    }

    @Override
    public String name() {
        return "ucli:" + user.user.replace(Consts.USER_PADDING, "");
    }

    @Override
    protected String toStringExtra() {
        return "," + remote.formatToIPPortString() + ",vni:" + user.vni + (connected ? "[UP]" : "[DOWN]");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserClientIface iface = (UserClientIface) o;
        return Objects.equals(user, iface.user) &&
            Objects.equals(remote, iface.remote);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, remote);
    }

    @Override
    public void init(IfaceInitParams params) throws Exception {
        super.init(params);

        DatagramFD cliSock = FDProvider.get().openDatagramFD();
        try {
            cliSock.connect(remote);
            cliSock.configureBlocking(false);
        } catch (IOException e) {
            try {
                cliSock.close();
            } catch (IOException t) {
                Logger.shouldNotHappen("close datagram sock when rolling back failed", t);
            }
            throw e;
        }
        setSock(cliSock, true);
        attachedToLoopAlert(params.loop);
    }

    @Override
    protected Aes256Key getEncryptionKey() {
        return key;
    }

    @Override
    public void sendPacket(PacketBuffer pkb) {
        if (bondLoop == null) {
            assert Logger.lowLevelDebug("bond loop is null, do not send data via this iface for now");
            return;
        }

        super.sendPacket(pkb);
    }

    @Override
    public void destroy() {
        if (isDestroyed()) {
            return;
        }
        super.destroy();
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

    @Override
    public int getOverhead() {
        return 28 /* vproxy header */ + 14 /* inner ethernet */ + 8 /* vxlan header */ + 8 /* udp header */ + 40 /* ipv6 header common */;
    }

    private class UserClientHandler implements Handler<DatagramFD> {
        private final ByteBuffer rcvBuf = Utils.allocateByteBuffer(SwitchUtils.TOTAL_RCV_BUF_LEN);
        private final UserClientIface iface = UserClientIface.this;

        private ConnectedToSwitchTimer connectedToSwitchTimer = null;
        private static final int toSwitchTimeoutSeconds = 60;

        private PeriodicEvent pingPeriodicEvent;
        private static final int pingPeriod = 20 * 1000;

        private class ConnectedToSwitchTimer extends Timer {
            public ConnectedToSwitchTimer(SelectorEventLoop loop) {
                super(loop, toSwitchTimeoutSeconds * 1000);
                iface.setConnected(true);
            }

            @Override
            public void cancel() {
                super.cancel();
                connectedToSwitchTimer = null;
                iface.setConnected(false);
            }
        }

        public UserClientHandler(SelectorEventLoop loop) {
            pingPeriodicEvent = loop.period(pingPeriod, this::sendPingPacket);
            sendPingPacket();
        }

        private void sendPingPacket() {
            VProxyEncryptedPacket p = new VProxyEncryptedPacket(iface.user.key);
            p.setMagic(Consts.VPROXY_SWITCH_MAGIC);
            p.setType(Consts.VPROXY_SWITCH_TYPE_PING);
            iface.sendVProxyPacket(p);
        }

        @Override
        public void accept(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void connected(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void readable(HandlerContext<DatagramFD> ctx) {
            DatagramFD sock = ctx.getChannel();
            while (true) {
                VProxyThread.current().newUuidDebugInfo();

                rcvBuf.limit(rcvBuf.capacity()).position(0);
                try {
                    sock.read(rcvBuf);
                } catch (IOException e) {
                    Logger.error(LogType.CONN_ERROR, "udp sock " + ctx.getChannel() + " got error when reading", e);
                    return;
                }
                if (rcvBuf.position() == 0) {
                    break; // nothing read, quit loop
                }

                VProxyEncryptedPacket p = new VProxyEncryptedPacket(iface.user.key);
                ByteArray arr = ByteArray.from(rcvBuf.array()).sub(0, rcvBuf.position());
                String err = p.from(new PacketDataBuffer(arr), true);
                if (err != null) {
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA, "received invalid packet from " + iface.name() + ": " + arr);
                    continue;
                }
                if (!p.getUser().equals(iface.user.user)) {
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA, "user in received packet from " + iface.name() + " mismatches, got " + p.getUser());
                    continue;
                }
                if (connectedToSwitchTimer == null) {
                    connectedToSwitchTimer = new ConnectedToSwitchTimer(ctx.getEventLoop());
                }
                connectedToSwitchTimer.resetTimer();
                if (p.getVxlan() == null) {
                    // not vxlan packet, ignore
                    continue;
                }
                if (p.getVxlan().getVni() != iface.user.vni) {
                    p.getVxlan().setVni(iface.user.vni);
                }
                PacketBuffer pkb = PacketBuffer.fromPacket(p.getVxlan());
                pkb.devin = iface;

                iface.statistics.incrRxPkts();
                iface.statistics.incrRxBytes(arr.length());

                received(pkb);
                callback.alertPacketsArrive(iface);
            }
        }

        @Override
        public void writable(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void removed(HandlerContext<DatagramFD> ctx) {
            iface.detachedFromLoopAlert();
            if (connectedToSwitchTimer != null) {
                connectedToSwitchTimer.cancel();
                connectedToSwitchTimer = null;
            }
            if (pingPeriodicEvent != null) {
                pingPeriodicEvent.cancel();
                pingPeriodicEvent = null;
            }
        }
    }
}
