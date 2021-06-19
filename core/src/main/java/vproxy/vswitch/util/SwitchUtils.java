package vproxy.vswitch.util;

import vproxy.base.util.ByteArray;
import vproxy.base.util.Consts;
import vproxy.base.util.Logger;
import vproxy.base.util.Utils;
import vproxy.vpacket.*;
import vproxy.vswitch.SocketBuffer;
import vproxy.vswitch.iface.Iface;
import vproxy.vswitch.iface.LocalSideVniGetterSetter;
import vproxy.vswitch.iface.RemoteSideVniGetterSetter;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class SwitchUtils {
    public static final int TOTAL_RCV_BUF_LEN = 4096;
    public static final int RCV_HEAD_PRESERVE_LEN = 512;

    private SwitchUtils() {
    }

    public static void updateBothSideVni(Iface iface, Iface newIface) {
        assert iface.equals(newIface);
        if (iface instanceof RemoteSideVniGetterSetter) {
            var that = (RemoteSideVniGetterSetter) newIface;
            if (that.getRemoteSideVni() != 0) {
                ((RemoteSideVniGetterSetter) iface).setRemoteSideVni(that.getRemoteSideVni());
            }
        }
        if (iface instanceof LocalSideVniGetterSetter) {
            var that = (LocalSideVniGetterSetter) newIface;
            var self = (LocalSideVniGetterSetter) iface;
            var newVal = that.getLocalSideVni(0);
            if (newVal != 0) {
                self.setLocalSideVni(newVal);
            }
        }
    }

    public static VXLanPacket getOrMakeVXLanPacket(SocketBuffer skb) {
        if (skb.vxlan == null) {
            var p = new VXLanPacket();
            p.setVni(skb.vni);
            p.setPacket(skb.pkt);
            skb.vxlan = p;
        }
        return skb.vxlan;
    }

    public static void checkAndUpdateMss(SocketBuffer skb, Iface iface) {
        int maxMss = iface.getBaseMTU() - iface.getOverhead() - 20 /* tcp common */;
        if (!(skb.pkt.getPacket() instanceof AbstractIpPacket) ||
            !(((AbstractIpPacket) skb.pkt.getPacket()).getPacket() instanceof TcpPacket)) {
            return; // only tcp requires modification
        }
        AbstractIpPacket ip = (AbstractIpPacket) skb.pkt.getPacket();
        TcpPacket tcp = (TcpPacket) ((AbstractIpPacket) skb.pkt.getPacket()).getPacket();
        if ((tcp.getFlags() & Consts.TCP_FLAGS_SYN) != Consts.TCP_FLAGS_SYN) {
            return; // only handle syn and syn-ack
        }
        if (tcp.getOptions() == null) {
            tcp.setOptions(new ArrayList<>(1));
        }
        boolean foundMss = false;
        for (TcpPacket.TcpOption opt : tcp.getOptions()) {
            if (opt.getKind() == Consts.TCP_OPTION_MSS) {
                foundMss = true;
                int originalMss = opt.getData().uint16(0);
                if (originalMss > maxMss) {
                    assert Logger.lowLevelDebug("tcp mss updated from " + originalMss + " to " + maxMss);
                    opt.setData(ByteArray.allocate(2).int16(0, maxMss));
                    tcp.clearRawPacket();
                    if (ip instanceof Ipv4Packet) {
                        tcp.buildIPv4TcpPacket((Ipv4Packet) ip);
                    } else {
                        tcp.buildIPv6TcpPacket((Ipv6Packet) ip);
                    }
                }
                break;
            }
        }
        if (foundMss) {
            return;
        }
        // need to add mss
        assert Logger.lowLevelDebug("add tcp mss: " + maxMss);
        TcpPacket.TcpOption opt = new TcpPacket.TcpOption();
        opt.setKind(Consts.TCP_OPTION_MSS);
        opt.setData(ByteArray.allocate(2).int16(0, maxMss));
        tcp.getOptions().add(opt);
        tcp.clearRawPacket();
        if (ip instanceof Ipv4Packet) {
            tcp.buildIPv4TcpPacket((Ipv4Packet) ip);
        } else {
            tcp.buildIPv6TcpPacket((Ipv6Packet) ip);
        }
    }

    public static void executeTapPostScript(String switchAlias, String dev, int vni, String postScript) throws Exception {
        if (postScript == null || postScript.isBlank()) {
            return;
        }
        ProcessBuilder pb = new ProcessBuilder().command(postScript);
        var env = pb.environment();
        env.put("DEV", dev);
        env.put("VNI", "" + vni);
        env.put("SWITCH", switchAlias);
        Process p = pb.start();
        Utils.pipeOutputOfSubProcess(p);
        p.waitFor(10, TimeUnit.SECONDS);
        if (p.isAlive()) {
            p.destroyForcibly();
            throw new Exception("the process took too long to execute");
        }
        int exit = p.exitValue();
        if (exit == 0) {
            return;
        }
        throw new Exception("exit code is " + exit);
    }
}
