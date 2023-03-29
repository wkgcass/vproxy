package io.vproxy.vswitch.node;

import io.vproxy.base.util.ByteArray;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vpacket.EthernetPacket;
import io.vproxy.vpacket.PacketDataBuffer;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.SwitchDelegate;
import io.vproxy.vswitch.VirtualNetwork;

public class NeighborResolve extends AbstractNeighborResolve {
    private final SwitchDelegate sw;

    public NeighborResolve(SwitchDelegate sw) {
        super("neighbor-resolve");
        this.sw = sw;
        dummyEthernetPacket.from(new PacketDataBuffer(ByteArray.from(
            0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0,
            0, 0
        )));
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.CONTINUE;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        return _returndropSkipErrorDrop();
    }

    private final EthernetPacket dummyEthernetPacket = new EthernetPacket();

    public void resolve(VirtualNetwork n, IP ip, MacAddress mac) {
        var pkb = PacketBuffer.fromPacket(n, dummyEthernetPacket);
        var res = resolve(n, ip, mac, pkb);
        if (res != HandleResult.DROP) {
            sw.scheduler.schedule(pkb);
        }
    }
}
