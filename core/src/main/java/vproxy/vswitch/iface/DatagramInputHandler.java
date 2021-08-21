package vproxy.vswitch.iface;

import vproxy.base.connection.Protocol;
import vproxy.base.selector.Handler;
import vproxy.base.selector.HandlerContext;
import vproxy.base.util.*;
import vproxy.base.util.coll.RingQueue;
import vproxy.base.util.thread.VProxyThread;
import vproxy.vfd.DatagramFD;
import vproxy.vfd.IPPort;
import vproxy.vpacket.PacketDataBuffer;
import vproxy.vpacket.VProxyEncryptedPacket;
import vproxy.vpacket.VXLanPacket;
import vproxy.vswitch.PacketBuffer;
import vproxy.vswitch.SwitchContext;
import vproxy.vswitch.util.SwitchUtils;
import vproxy.vswitch.util.UserInfo;

import java.io.IOException;
import java.nio.ByteBuffer;

public class DatagramInputHandler implements Handler<DatagramFD> {
    private static final int TOTAL_LEN = SwitchUtils.TOTAL_RCV_BUF_LEN;
    private static final int PRESERVED_LEN = SwitchUtils.RCV_HEAD_PRESERVE_LEN;

    private final SwitchContext swCtx;
    private final ByteBuffer rcvBuf = Utils.allocateByteBuffer(TOTAL_LEN);
    private final ByteArray raw = ByteArray.from(rcvBuf.array());
    private final RingQueue<PacketBuffer> rcvQ = new RingQueue<>(1);

    public DatagramInputHandler(SwitchContext swCtx) {
        this.swCtx = swCtx;
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
        readable0(ctx);
    }

    private void readable0(HandlerContext<DatagramFD> ctx) {
        DatagramFD sock = ctx.getChannel();
        while (true) {
            VProxyThread.current().newUuidDebugInfo();

            rcvBuf.limit(TOTAL_LEN).position(PRESERVED_LEN);
            IPPort remote;
            try {
                remote = sock.receive(rcvBuf);
            } catch (IOException e) {
                Logger.error(LogType.CONN_ERROR, "udp sock " + ctx.getChannel() + " got error when reading", e);
                return;
            }
            if (rcvBuf.position() == PRESERVED_LEN) {
                break; // nothing read, quit loop
            }

            var pkb = handleNetworkAndGetPKB(remote);
            if (pkb == null) {
                assert Logger.lowLevelDebug("no pkb provided, ignore");
                continue;
            }
            rcvQ.add(pkb);
            swCtx.alertPacketsArrive(rcvQ);
        }
    }

    private PacketBuffer handleNetworkAndGetPKB(IPPort remote) {
        ByteArray data = raw.sub(PRESERVED_LEN, rcvBuf.position() - PRESERVED_LEN);

        VProxyEncryptedPacket packet = new VProxyEncryptedPacket(uname -> {
            var info = swCtx.getUserInfo(uname);
            if (info == null) return null;
            return info.key;
        });
        PacketBuffer pkb;

        String err = packet.from(new PacketDataBuffer(data), true);
        assert Logger.lowLevelDebug("packet.from(data) = " + err);
        if (err == null) {
            String user = packet.getUser();
            UserInfo info = swCtx.getUserInfo(user);
            if (info == null) {
                Logger.warn(LogType.SYS_ERROR, "concurrency detected: user info is null while parsing the packet succeeded: " + user);
                return null;
            }

            // get iface from recorded userifaces
            UserIface uiface = null;
            for (Iface recorded : swCtx.getIfaces()) {
                // find existing userIface
                if (recorded instanceof UserIface) {
                    var recordedUIface = (UserIface) recorded;
                    if (recordedUIface.user.equals(user)) {
                        if (recordedUIface.udpSockAddress.getAddress().equals(remote.getAddress())) {
                            if (recordedUIface.udpSockAddress.getPort() == remote.getPort()) {
                                assert Logger.lowLevelDebug("using existing uiface: " + recordedUIface);
                                uiface = recordedUIface;
                                break;
                            }
                        }
                        Logger.warn(LogType.ALERT, "new connection established for user " + user + ", the old one will be disconnected");
                        swCtx.destroyIface(recordedUIface);
                        break;
                    }
                }
            }
            if (uiface == null) {
                uiface = new UserIface(remote, user);
                try {
                    swCtx.initIface(uiface);
                } catch (Exception e) {
                    Logger.error(LogType.SYS_ERROR, "init " + uiface + " failed", e);
                    return null;
                }
                uiface.setBaseMTU(info.defaultMtu);
                uiface.setFloodAllowed(info.defaultFloodAllowed);
            }
            uiface.setLocalSideVni(info.vni);

            assert Logger.lowLevelDebug("got packet " + packet + " from " + uiface);

            VXLanPacket vxLanPacket = packet.getVxlan();
            if (vxLanPacket != null) {
                int packetVni = vxLanPacket.getVni();
                uiface.setRemoteSideVni(packetVni); // set vni to the iface
                assert Logger.lowLevelDebug("setting vni for " + user + " to " + info.vni);
                if (packetVni != info.vni) {
                    vxLanPacket.setVni(info.vni);
                }
            }

            if (packet.getType() == Consts.VPROXY_SWITCH_TYPE_PING) {
                assert Logger.lowLevelDebug("is vproxy ping message, do reply");
                sendPingTo(uiface);
                // should reset timeout
                swCtx.recordIface(uiface); // use record iface to reset the timer
                return null;
            }

            pkb = PacketBuffer.fromPacket(vxLanPacket);
            pkb.devin = uiface;
            // fall through
        } else {
            assert Logger.lowLevelDebug("is vxlan packet");

            // check whether it's coming from remote switch
            Iface remoteSwitch = null;
            for (Iface i : swCtx.getIfaces()) {
                if (!(i instanceof RemoteSwitchIface)) {
                    continue;
                }
                RemoteSwitchIface rsi = (RemoteSwitchIface) i;
                if (remote.equals(rsi.udpSockAddress)) {
                    remoteSwitch = i;
                    break;
                }
            }

            Iface iface;
            boolean isNewIface = false;
            if (remoteSwitch != null) {
                iface = remoteSwitch;
            } else {
                assert Logger.lowLevelDebug("is bare vxlan");
                if (!swCtx.sw.bareVXLanAccess.allow(Protocol.UDP, remote.getAddress(), swCtx.sw.vxlanBindingAddress.getPort())) {
                    assert Logger.lowLevelDebug("not in allowed security-group, drop it");
                    return null;
                }
                assert Logger.lowLevelDebug("passes securityGroup check");

                // is from a vxlan endpoint
                BareVXLanIface biface = null;
                for (Iface i : swCtx.getIfaces()) {
                    if (!(i instanceof BareVXLanIface)) {
                        continue;
                    }
                    BareVXLanIface bi = (BareVXLanIface) i;
                    if (remote.equals(bi.udpSockAddress)) {
                        biface = bi;
                        break;
                    }
                }
                if (biface == null) {
                    biface = new BareVXLanIface(remote);
                    isNewIface = true;
                }
                iface = biface;
            }

            // try to parse into vxlan directly
            pkb = PacketBuffer.fromVXLanBytes(iface, raw, PRESERVED_LEN, TOTAL_LEN - rcvBuf.position());
            err = pkb.init();
            if (err != null) {
                assert Logger.lowLevelDebug("invalid packet for vxlan: " + err + ", drop it");
                return null;
            }

            if (iface instanceof BareVXLanIface) { // additional check
                BareVXLanIface biface = (BareVXLanIface) iface;
                if (isNewIface) {
                    biface.setLocalSideVni(pkb.vni);
                } else {
                    int ifaceVni = biface.getLocalSideVni(pkb.vni);
                    if (ifaceVni != pkb.vni) {
                        Logger.warn(LogType.INVALID_EXTERNAL_DATA,
                            "received vxlan packet from " + remote + " " +
                                "but originally vni is " + ifaceVni + ", currently " + pkb.vni);
                        return null;
                    }
                }

                // distinguish bare vxlan sock and switch vxlan link
                int r1 = pkb.vxlan.getReserved1();
                final int I_AM_FROM_SWITCH = Consts.I_AM_FROM_SWITCH;
                if ((r1 & I_AM_FROM_SWITCH) == I_AM_FROM_SWITCH) {
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA,
                        "received a packet which should come from a remote switch, " +
                            "but actually coming from bare vxlan sock: " + iface + " with packet " + pkb.vxlan);
                    return null; // drop
                }
            }

            if (isNewIface) {
                try {
                    swCtx.initIface(iface);
                } catch (Exception e) {
                    Logger.error(LogType.SYS_ERROR, "init " + iface + " failed", e);
                    return null;
                }
            }
        }
        assert Logger.lowLevelDebug("got packet " + pkb + " from " + remote);
        return pkb;
    }

    private void sendPingTo(UserIface iface) {
        assert Logger.lowLevelDebug("sendPingTo(" + iface + ")");
        VProxyEncryptedPacket p = new VProxyEncryptedPacket(uname -> {
            var info = swCtx.getUserInfo(uname);
            if (info == null) return null;
            return info.key;
        });
        p.setMagic(Consts.VPROXY_SWITCH_MAGIC);
        p.setType(Consts.VPROXY_SWITCH_TYPE_PING);
        iface.sendVProxyPacket(p);
    }

    @Override
    public void writable(HandlerContext<DatagramFD> ctx) {
        // will not fire
    }

    @Override
    public void removed(HandlerContext<DatagramFD> ctx) {
        Logger.error(LogType.IMPROPER_USE, "the udp sock " + ctx.getChannel() + " is removed from loop," +
            "the loop is considered to be closed, it's required to terminate all ifaces");
        swCtx.loopRemovalStop();
    }
}
