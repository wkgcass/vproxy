package io.vproxy.vswitch.iface;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.callback.BlockCallback;
import io.vproxy.base.util.coll.RingQueue;
import io.vproxy.vfd.FDProvider;
import io.vproxy.vpacket.EthernetPacket;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.VirtualNetwork;

public class ProgramIface extends Iface {
    public final String alias;
    private final VirtualNetwork network;
    private final RingQueue<ReceivedPacket> receivedPackets = new RingQueue<>();
    private SelectorEventLoop loop;

    public ProgramIface(String alias, VirtualNetwork network) {
        this.alias = alias;
        this.network = network;
    }

    @Override
    public void init(IfaceInitParams params) throws Exception {
        super.init(params);
        this.loop = params.loop;
    }

    public static class ReceivedPacket {
        public final long ts;
        public final EthernetPacket pkt;

        public ReceivedPacket(long ts, EthernetPacket pkt) {
            this.ts = ts;
            this.pkt = pkt;
        }
    }

    public ReceivedPacket poll() {
        var cb = new BlockCallback<ReceivedPacket, RuntimeException>();
        loop.runOnLoop(() -> {
            var rp = receivedPackets.poll();
            cb.succeeded(rp);
        });
        return cb.block();
    }

    public void injectPacket(EthernetPacket pkt) {
        var pkb = PacketBuffer.fromPacket(network, pkt);
        pkb.devin = this;
        loop.runOnLoop(() -> {
            received(pkb);
            callback.alertPacketsArrive(this);
        });
    }

    @Override
    public void sendPacket(PacketBuffer pkb) {
        Logger.alert("ProgramIface " + name() + " sendPacket: " + pkb);
        receivedPackets.add(new ReceivedPacket(FDProvider.get().currentTimeMillis(), pkb.pkt));
    }

    @Override
    public int getLocalSideVni(int hint) {
        return network.vni;
    }

    @Override
    public int getOverhead() {
        return 0;
    }

    @Override
    public String name() {
        return "program:" + alias;
    }
}
