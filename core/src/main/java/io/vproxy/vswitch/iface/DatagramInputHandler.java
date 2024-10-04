package io.vproxy.vswitch.iface;

import io.vproxy.base.connection.Protocol;
import io.vproxy.base.selector.Handler;
import io.vproxy.base.selector.HandlerContext;
import io.vproxy.base.util.*;
import io.vproxy.base.util.coll.RingQueue;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.vfd.DatagramFD;
import io.vproxy.vfd.IPPort;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.SwitchDelegate;
import io.vproxy.vswitch.util.SwitchUtils;

import java.io.IOException;
import java.nio.ByteBuffer;

public class DatagramInputHandler implements Handler<DatagramFD> {
    private static final int TOTAL_LEN = SwitchUtils.TOTAL_RCV_BUF_LEN;
    private static final int PRESERVED_LEN = SwitchUtils.RCV_HEAD_PRESERVE_LEN;

    private final SwitchDelegate swCtx;
    private final ByteBuffer rcvBuf = Utils.allocateByteBuffer(TOTAL_LEN);
    private final ByteArray raw = ByteArray.from(rcvBuf.array());
    private final RingQueue<PacketBuffer> rcvQ = new RingQueue<>(1);

    public DatagramInputHandler(SwitchDelegate swCtx) {
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

            pkb.devin.statistics.incrRxPkts();
            if (pkb.pktBuf != null) {
                pkb.devin.statistics.incrRxBytes(pkb.pktBuf.length());
            }

            rcvQ.add(pkb);
            swCtx.alertPacketsArrive(rcvQ);
        }
    }

    private PacketBuffer handleNetworkAndGetPKB(IPPort remote) {
        PacketBuffer pkb;
        assert Logger.lowLevelDebug("handle vxlan packet");
        {
            // check whether it's coming from remote switch
            Iface remoteSwitch = null;
            for (Iface i : swCtx.getIfaces()) {
                if (!(i instanceof RemoteSwitchIface rsi)) {
                    continue;
                }
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
                    if (!(i instanceof BareVXLanIface bi)) {
                        continue;
                    }
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
            var err = pkb.init();
            if (err != null) {
                assert Logger.lowLevelDebug("invalid packet for vxlan: " + err + ", drop it");
                return null;
            }

            if (iface instanceof BareVXLanIface biface) { // additional check
                if (isNewIface) {
                    biface.setLocalSideVrf(pkb.vrf);
                } else {
                    int ifaceVrf = biface.getLocalSideVrf(pkb.vrf);
                    if (ifaceVrf != pkb.vrf) {
                        Logger.warn(LogType.INVALID_EXTERNAL_DATA,
                            "received vxlan packet from " + remote + " " +
                                "but originally vrf is " + ifaceVrf + ", currently " + pkb.vrf);
                        return null;
                    }
                }

                // distinguish bare vxlan sock and switch vxlan link
                int r1 = pkb.vxlan.getReserved1();
                final int I_AM_FROM_SWITCH = Consts.I_AM_FROM_SWITCH;
                if ((r1 & I_AM_FROM_SWITCH) == I_AM_FROM_SWITCH) {
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA,
                        "received a packet which should come from a remote switch, " +
                            "but actually coming from bare vxlan sock: " + iface.name() + " with packet " + pkb.vxlan);
                    return null; // drop
                }
            }

            if (isNewIface) {
                try {
                    swCtx.initIface(iface);
                } catch (Exception e) {
                    Logger.error(LogType.SYS_ERROR, "init " + iface.name() + " failed", e);
                    return null;
                }
            }
        }
        assert Logger.lowLevelDebug("got packet " + pkb + " from " + remote);
        return pkb;
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
