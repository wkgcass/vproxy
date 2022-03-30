package io.vproxy.vswitch;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.net.IPPortPool;
import io.vproxy.base.util.ratelimit.RateLimiter;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.AbstractPacket;
import io.vproxy.vpacket.conntrack.tcp.TcpNat;
import io.vproxy.vpacket.conntrack.tcp.TcpTimeout;
import io.vproxy.vswitch.iface.Iface;
import io.vproxy.vswitch.plugin.FilterResult;
import io.vproxy.vswitch.util.SwitchUtils;

public class PacketFilterHelper {
    public PacketFilterHelper(
        SwitchContext.SendingPacket sendPacketFunc
    ) {
        this.sendPacketFunc = sendPacketFunc;
    }

    public interface SendingPacket {
        void send(PacketBuffer pkb, Iface iface);
    }

    private final SwitchContext.SendingPacket sendPacketFunc;

    public void sendPacket(PacketBuffer pkb, Iface toIface) {
        if (toIface == null) {
            return;
        }
        pkb.ensurePartialPacketParsed();
        sendPacketFunc.send(pkb.copy(), toIface);
    }

    public FilterResult redirect(PacketBuffer pkb, Iface iface) {
        if (iface == null) return FilterResult.DROP;
        pkb.devredirect = iface;
        return FilterResult.REDIRECT;
    }

    public boolean ratelimitByBitsPerSecond(PacketBuffer pkb, RateLimiter rl) {
        int bytes;
        if (pkb.pktBuf != null) {
            bytes = pkb.pktBuf.length();
        } else {
            bytes = pkb.pkt.getRawPacket(AbstractPacket.FLAG_CHECKSUM_UNNECESSARY).length();
        }
        int bits = bytes * 8;
        return rl.acquire(bits);
    }

    public boolean ratelimitByPacketsPerSecond(@SuppressWarnings("unused") PacketBuffer pkb, RateLimiter rl) {
        return rl.acquire(1);
    }

    public boolean isNatTracked(PacketBuffer pkb) {
        if (pkb.tcpPkt != null) {
            return isTcpNatTracked(pkb);
        } else if (pkb.udpPkt != null) {
            // TODO
        }
        return false;
    }

    private boolean isTcpNatTracked(PacketBuffer pkb) {
        if (pkb.tcpPkt == null) {
            assert Logger.lowLevelDebug("isTcpNat: no tcpPkt");
            return false;
        }
        var tcp = pkb.network.conntrack.lookupTcp(pkb.ipPkt, pkb.tcpPkt);
        if (tcp == null) {
            assert Logger.lowLevelDebug("isTcpNat: no tcp entry");
            return false;
        }
        var nat = tcp.getNat();
        if (nat == null) {
            assert Logger.lowLevelDebug("isTcpNat: no nat record");
            return false;
        }
        assert Logger.lowLevelDebug("isTcpNat: yes");
        pkb.tcp = tcp;
        pkb.tcpNat = nat;
        return true;
    }

    public boolean executeNat(PacketBuffer pkb) {
        if (pkb.tcpPkt != null) {
            if (pkb.tcpNat == null) {
                isTcpNatTracked(pkb); // try to get tcp nat record
            }
            if (pkb.tcpNat != null) {
                SwitchUtils.executeTcpNat(pkb, pkb.tcpNat);
                return true;
            } else {
                return false;
            }
        } else {
            // TODO
            return false;
        }
    }

    public boolean executeDNat(PacketBuffer pkb, IPPort dst) {
        if (pkb.tcpPkt != null) {
            if (pkb.tcpNat != null || isTcpNatTracked(pkb)) {
                return executeNat(pkb);
            }
            newTcpDNat(pkb, dst);
            return true;
        } else if (pkb.udpPkt != null) {
            // TODO
            return true;
        } else {
            assert Logger.lowLevelDebug("cannot handle dnat for packet " + pkb.pkt);
            return false;
        }
    }

    private void newTcpDNat(PacketBuffer pkb, IPPort dst) {
        var _1 = pkb.network.conntrack.recordTcp(
            new IPPort(pkb.ipPkt.getSrc(), pkb.tcpPkt.getSrcPort()),
            new IPPort(pkb.ipPkt.getDst(), pkb.tcpPkt.getDstPort())
        );
        var _2 = pkb.network.conntrack.recordTcp(
            dst,
            new IPPort(pkb.ipPkt.getSrc(), pkb.tcpPkt.getSrcPort())
        );
        var nat = new TcpNat(_1, _2, pkb.network.conntrack, TcpTimeout.DEFAULT);
        _1.setNat(nat);
        _2.setNat(nat);
        SwitchUtils.executeTcpNat(pkb, nat);
    }

    public boolean executeSNat(PacketBuffer pkb, IPPortPool srcPool) {
        if (pkb.tcpPkt != null) {
            if (pkb.tcpNat != null || isTcpNatTracked(pkb)) {
                return executeNat(pkb);
            }
            newTcpSNat(pkb, srcPool);
            return true;
        } else if (pkb.udpPkt != null) {
            // TODO
            return true;
        } else {
            Logger.error(LogType.IMPROPER_USE, "cannot handle snat for packet " + pkb.pkt);
            return false;
        }
    }

    private void newTcpSNat(PacketBuffer pkb, IPPortPool srcPool) {
        IPPort src = srcPool.allocate();
        var _1 = pkb.network.conntrack.recordTcp(
            new IPPort(pkb.ipPkt.getSrc(), pkb.tcpPkt.getSrcPort()),
            new IPPort(pkb.ipPkt.getDst(), pkb.tcpPkt.getDstPort())
        );
        var _2 = pkb.network.conntrack.recordTcp(
            new IPPort(pkb.ipPkt.getDst(), pkb.tcpPkt.getDstPort()),
            src
        );
        var nat = new TcpNat(_1, _2, srcPool, true, pkb.network.conntrack, TcpTimeout.DEFAULT);
        _1.setNat(nat);
        _2.setNat(nat);
        SwitchUtils.executeTcpNat(pkb, nat);
    }

    public boolean executeFNat(PacketBuffer pkb, IPPortPool srcPool, IPPort dst) {
        if (pkb.tcpPkt != null) {
            if (pkb.tcpNat != null || isTcpNatTracked(pkb)) {
                return executeNat(pkb);
            }
            newTcpFNat(pkb, srcPool, dst);
            return true;
        } else if (pkb.udpPkt != null) {
            // TODO
            return true;
        } else {
            Logger.error(LogType.IMPROPER_USE, "cannot handle fnat for packet " + pkb.pkt);
            return false;
        }
    }

    private void newTcpFNat(PacketBuffer pkb, IPPortPool srcPool, IPPort dst) {
        IPPort src = srcPool.allocate();
        var _1 = pkb.network.conntrack.recordTcp(
            new IPPort(pkb.ipPkt.getSrc(), pkb.tcpPkt.getSrcPort()),
            new IPPort(pkb.ipPkt.getDst(), pkb.tcpPkt.getDstPort())
        );
        var _2 = pkb.network.conntrack.recordTcp(dst, src);
        var nat = new TcpNat(_1, _2, srcPool, true, pkb.network.conntrack, TcpTimeout.DEFAULT);
        _1.setNat(nat);
        _2.setNat(nat);
        SwitchUtils.executeTcpNat(pkb, nat);
    }
}
