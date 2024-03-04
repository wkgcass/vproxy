package io.vproxy.vswitch.iface;

import io.vproxy.base.util.*;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.base.util.exception.XException;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.fubuki.Fubuki;
import io.vproxy.fubuki.FubukiCallback;
import io.vproxy.vfd.*;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.Switch;
import io.vproxy.vswitch.VirtualNetwork;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FubukiTunIface extends TunIface {
    public final String nodeName;
    public final IPPort serverIPPort;
    public final IPMask localAddr;
    public final String key;
    private Fubuki fubuki;
    private Switch sw;

    public FubukiTunIface(int localSideVni, MacAddress mac,
                          String nodeName, IPPort serverIPPort,
                          IPMask localAddr,
                          String key) {
        super("", localSideVni, mac, null);
        this.nodeName = nodeName;
        this.serverIPPort = serverIPPort;
        this.localAddr = localAddr;
        this.key = key;
    }

    @Override
    public void init(IfaceInitParams params) throws Exception {
        super.init(params);
        fubuki = new Fubuki(getIndex(), nodeName, serverIPPort, key, localAddr, new Callback());
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
        return super.toStringExtra() + ",server=" + serverIPPort.formatToIPPortString() + ",local=" + localAddr.formatToIPMaskString();
    }

    @Override
    public void destroy() {
        super.destroy();
        if (fubuki != null) {
            fubuki.close();
            fubuki = null;
        }
        clearManagedIPs();
    }

    private final Set<IP> managedIPs = new HashSet<>();

    private void clearManagedIPs() {
        if (sw == null) { // not initialized yet
            return;
        }
        VirtualNetwork net;
        try {
            net = sw.getNetwork(localSideVni);
        } catch (NotFoundException ignore) {
            return;
        }
        for (var ip : managedIPs) {
            try {
                net.ips.deleteWithCondition(ip, ipmac -> ipmac.annotations.owner.equals("fubuki"));
            } catch (NotFoundException ignore) {
            }
        }
    }

    private class Callback implements FubukiCallback {
        @Override
        public void onPacket(Fubuki fubuki, ByteArray packet) {
            final int PRE_PADDING = 32;
            var p = ByteArray.allocate(PRE_PADDING).concat(packet).copy();
            bondLoop.runOnLoop(() -> {
                VProxyThread.current().newUuidDebugInfo();
                var pkb = PacketBuffer.fromIpBytes(FubukiTunIface.this, localSideVni, p, PRE_PADDING, 0);
                receivedPacket(pkb);
            });
        }

        @Override
        public void addAddress(Fubuki fubuki, IPv4 ip, IPv4 mask) {
            bondLoop.runOnLoop(() -> {
                Logger.warn(LogType.ALERT, STR."fubuki is trying to add ip \{ip} to vpc \{localSideVni}");
                VirtualNetwork net;
                try {
                    net = sw.getNetwork(localSideVni);
                } catch (NotFoundException ignore) {
                    Logger.error(LogType.IMPROPER_USE, STR."network \{localSideVni} does not exist, ip \{ip} will not be added, you will have to add the ip manually");
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
                Logger.warn(LogType.ALERT, STR."fubuki is trying to remove ip \{ip} from vpc \{localSideVni}");
                VirtualNetwork net;
                try {
                    net = sw.getNetwork(localSideVni);
                } catch (NotFoundException ignore) {
                    return;
                }
                try {
                    var ok = net.ips.deleteWithCondition(ip, ipmac -> ipmac.annotations.owner.equals("fubuki"));
                    if (!ok) {
                        Logger.warn(LogType.INVALID_EXTERNAL_DATA, STR."ip \{ip} in \{net} is not owned by fubuki but tried to remove");
                    }
                } catch (NotFoundException ignore) {
                }
            });
        }
    }
}
