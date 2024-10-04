package io.vproxy.vswitch.iface;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.exception.PreconditionUnsatisfiedException;
import io.vproxy.pni.Allocator;
import io.vproxy.vfd.IPv4;
import io.vproxy.vpacket.EtherIPPacket;
import io.vproxy.vpacket.Ipv4Packet;
import io.vproxy.vswitch.PacketBuffer;

import java.lang.foreign.MemorySegment;

public class FubukiEtherIPIface extends Iface implements SubIface {
    private final FubukiTunIface parent;
    public final IPv4 targetIP;
    public final int localSideVrf;

    public FubukiEtherIPIface(FubukiTunIface parent, IPv4 targetIP, int localSideVrf) throws PreconditionUnsatisfiedException {
        if (parent.getLocalAddr() == null) {
            throw new PreconditionUnsatisfiedException("local addr of the fubuki iface " + parent.name() + " is not retrieved yet");
        }

        this.parent = parent;
        this.targetIP = targetIP.stripHostname();
        this.localSideVrf = localSideVrf;
    }

    @Override
    public void sendPacket(PacketBuffer pkb) {
        var ipPkt = new Ipv4Packet();
        ipPkt.setVersion(4);
        ipPkt.setTtl(64);
        ipPkt.setProtocol(Consts.IP_PROTOCOL_ETHERIP);
        ipPkt.setSrc(parent.getLocalAddr().ip().to4());
        ipPkt.setDst(targetIP);
        var etherip = new EtherIPPacket();
        ipPkt.setPacket(etherip);
        etherip.setPacket(pkb.pkt);

        var bytes = ipPkt.getRawPacket(0);
        try (var allocator = Allocator.ofConfined()) {
            var seg = allocator.allocate(bytes.length());
            seg.copyFrom(MemorySegment.ofArray(bytes.toJavaArray()));
            parent.fubuki.send(seg);
        }

        statistics.incrTxBytes(bytes.length());
        statistics.incrTxPkts();
    }

    @Override
    public int getLocalSideVrf(int hint) {
        return localSideVrf;
    }

    @Override
    public int getOverhead() {
        return parent.getOverhead() + 2 /* etherip */ + 14 /* inner ethernet packet */;
    }

    @Override
    public String name() {
        return "fubuki-etherip/" + targetIP.formatToIPString() + "@" + parent.name();
    }

    @Override
    protected String toStringExtra() {
        return ",vrf:" + localSideVrf;
    }

    @Override
    public Iface getParentIface() {
        return parent;
    }

    private boolean ready = false;

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public void setReady() {
        ready = true;
    }

    @Override
    public void destroy() {
        if (isDestroyed()) {
            return;
        }
        super.destroy();
        parent.removeFubukiCable(this);
        callback.alertDeviceDown(this);
    }

    void onPacket(ByteArray packet, int off, int pad) {
        var pkb = PacketBuffer.fromEtherBytes(this, localSideVrf, packet, off, pad);
        var err = pkb.init();
        if (err != null) {
            assert Logger.lowLevelDebug("got invalid packet: " + err);
            return;
        }
        received(pkb);

        statistics.incrRxBytes(pkb.pktBuf.length());
        statistics.incrRxPkts();

        callback.alertPacketsArrive(this);
    }
}
