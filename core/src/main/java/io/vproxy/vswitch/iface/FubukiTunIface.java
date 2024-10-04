package io.vproxy.vswitch.iface;

import io.vproxy.base.util.*;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.base.util.exception.PreconditionUnsatisfiedException;
import io.vproxy.base.util.exception.XException;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.fubuki.Fubuki;
import io.vproxy.fubuki.FubukiCallback;
import io.vproxy.vfd.*;
import io.vproxy.vpacket.EtherIPPacket;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.Switch;
import io.vproxy.vswitch.VirtualNetwork;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.*;

public class FubukiTunIface extends TunIface {
    public final String nodeName;
    public final IPPort serverIPPort;
    private IPMask localAddr;
    public final String key;
    Fubuki fubuki;
    private Switch sw;

    public FubukiTunIface(int localSideVrf, MacAddress mac,
                          String nodeName, IPPort serverIPPort,
                          IPMask localAddr,
                          String key) {
        super("", localSideVrf, mac, null);
        this.nodeName = nodeName;
        this.serverIPPort = serverIPPort;
        this.localAddr = localAddr;
        this.key = key;
    }

    public IPMask getLocalAddr() {
        return localAddr;
    }

    @Override
    public void init(IfaceInitParams params) throws Exception {
        super.init(params);
        fubuki = new Fubuki(new Fubuki.Data(getIndex(), nodeName, serverIPPort, key, localAddr, new Callback()));
        sw = params.sw;
        bondLoop = params.loop;
    }

    @Override
    protected boolean customInitSteps() {
        return true;
    }

    @Override
    protected boolean requireAFHeader() {
        return false;
    }

    @Override
    public int getOverhead() {
        return 6 /* fubuki header */ + 8 /* udp header */ + 40 /* ipv6 header common */;
    }

    @Override
    protected void sendPacket(ByteBuffer data) throws IOException {
        var seg = MemorySegment.ofBuffer(data);
        fubuki.send(seg);
    }

    @Override
    public String name() {
        return "fubuki:" + nodeName;
    }

    @Override
    protected String toStringExtra() {
        var fubuki = this.fubuki;
        var status = "off";
        if (fubuki != null && fubuki.isRunning()) {
            status = "on";
        }
        return super.toStringExtra() + ",server=" + serverIPPort.formatToIPPortString() + ",local=" + (
            localAddr == null ? "null" : localAddr.formatToIPMaskString()
        ) + ",status=" + status;
    }

    @Override
    public void destroy() {
        super.destroy();
        for (var key : new ArrayList<>(etheripSubIfaces.keySet())) {
            var cable = etheripSubIfaces.get(key);
            cable.destroy();
        }
        if (fubuki != null) {
            fubuki.close();
            fubuki = null;
        }
    }

    private final Map<IPv4, FubukiEtherIPIface> etheripSubIfaces = new HashMap<>();

    public FubukiEtherIPIface addEtherIPSubIface(IPv4 ip, int vrf) throws AlreadyExistException, PreconditionUnsatisfiedException {
        ip = ip.stripHostname();
        if (etheripSubIfaces.containsKey(ip)) {
            throw new AlreadyExistException("fubuki-cable", ip.formatToIPString());
        }
        var iface = new FubukiEtherIPIface(this, ip, vrf);
        etheripSubIfaces.put(ip, iface);
        return iface;
    }

    void removeFubukiCable(FubukiEtherIPIface iface) {
        etheripSubIfaces.remove(iface.targetIP);
    }

    private final Set<IPv4> managedIPs = new HashSet<>();

    private void clearManagedIPs() {
        if (sw == null) { // not initialized yet
            return;
        }
        VirtualNetwork net;
        try {
            net = sw.getNetwork(localSideVrf);
        } catch (NotFoundException ignore) {
            return;
        }
        for (var ip : managedIPs) {
            try {
                net.ips.deleteWithCondition(ip, ipmac -> ipmac.annotations.owner.equals("fubuki"));
            } catch (NotFoundException ignore) {
            }
        }
        managedIPs.clear();
    }

    private class Callback implements FubukiCallback {
        private static final ByteArray PRE_PADDING = ByteArray.allocate(32);
        private static final ByteArray POST_PADDING = ByteArray.allocate(32);

        @Override
        public void onPacket(Fubuki fubuki, ByteArray packet) {
            var p = PRE_PADDING.concat(packet).concat(POST_PADDING).copy().arrange();
            bondLoop.runOnLoop(() -> {
                VProxyThread.current().newUuidDebugInfo();
                var pkb = PacketBuffer.fromIpBytes(FubukiTunIface.this, localSideVrf, p, PRE_PADDING.length(), POST_PADDING.length());
                var initErr = pkb.init();

                if (initErr == null) {
                    if (pkb.ipPkt != null && pkb.ipPkt.getPacket() instanceof EtherIPPacket) {
                        assert Logger.lowLevelDebug("received etherip packet from " + this);
                        //noinspection SuspiciousMethodCalls
                        var etherip = etheripSubIfaces.get(pkb.ipPkt.getSrc());
                        if (etherip != null && etherip.isReady()) {
                            assert Logger.lowLevelDebug("redirecting etherip packet to " + etherip);

                            etherip.onPacket(p, PRE_PADDING.length() + pkb.ipPkt.getHeaderSize() + 2 /* etherip header */, POST_PADDING.length());
                            return;
                        }
                    }
                } else {
                    assert Logger.lowLevelDebug("unable to init the pkb because error is thrown: " + initErr);
                }

                assert Logger.lowLevelDebug("this packet is not an etherip packet or no handler iface attached or subif is not ready, handle normally");
                receivedPacket(pkb);
            });
        }

        @Override
        public void addAddress(Fubuki fubuki, IPv4 ip, IPv4 mask) {
            bondLoop.runOnLoop(() -> {
                Logger.warn(LogType.ALERT, "fubuki is trying to add ip " + ip + " to vrf " + localSideVrf);
                localAddr = new IPMask(ip, mask);
                VirtualNetwork net;
                try {
                    net = sw.getNetwork(localSideVrf);
                } catch (NotFoundException ignore) {
                    Logger.error(LogType.IMPROPER_USE, "network " + localSideVrf + " does not exist, ip " + ip + " will not be added, you will have to add the ip manually");
                    return;
                }
                try {
                    net.addIp(ip,
                        new MacAddress(new byte[]{
                            0x00, (byte) 0xfb,
                            ip.bytes.get(0),
                            ip.bytes.get(1),
                            ip.bytes.get(2),
                            ip.bytes.get(3)
                        }),
                        new Annotations(Map.of(
                            AnnotationKeys.Owner.name, "fubuki",
                            AnnotationKeys.NoSave.name, "true"
                        )));
                    managedIPs.add(ip);
                } catch (AlreadyExistException | XException e) {
                    Logger.error(LogType.SYS_ERROR, "failed to add ip from fubuki: " + ip, e);
                }
            });
        }

        @Override
        public void deleteAddress(Fubuki fubuki, IPv4 ip, IPv4 mask) {
            bondLoop.runOnLoop(() -> {
                Logger.warn(LogType.ALERT, "fubuki is trying to remove ip " + ip + " from vrf " + localSideVrf);
                VirtualNetwork net;
                try {
                    net = sw.getNetwork(localSideVrf);
                } catch (NotFoundException ignore) {
                    return;
                }
                try {
                    var ok = net.ips.deleteWithCondition(ip, ipmac -> ipmac.annotations.owner.equals("fubuki"));
                    if (!ok) {
                        Logger.warn(LogType.INVALID_EXTERNAL_DATA, "ip " + ip + " in " + net + " is not owned by fubuki but tried to remove");
                    }
                } catch (NotFoundException ignore) {
                }
            });
        }

        @Override
        public void terminate(Fubuki fubuki) {
            bondLoop.runOnLoop(() -> {
                Logger.warn(LogType.ALERT, "fubuki is terminated, clearing ips: " + managedIPs);
                clearManagedIPs();
            });
        }
    }
}
