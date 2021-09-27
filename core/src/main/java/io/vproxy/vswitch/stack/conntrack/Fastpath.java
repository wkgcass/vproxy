package io.vproxy.vswitch.stack.conntrack;

import io.vproxy.base.util.Consts;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vpacket.EthernetPacket;
import io.vproxy.vpacket.Ipv4Packet;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.SwitchContext;
import io.vproxy.vswitch.iface.Iface;

public class Fastpath {
    public final Iface output;
    public final int vni;
    public final MacAddress local;
    public final MacAddress remote;

    public Fastpath(Iface output, int vni, MacAddress local, MacAddress remote) {
        this.output = output;
        this.vni = vni;
        this.local = local;
        this.remote = remote;
    }

    public boolean validateAndSetInto(SwitchContext swCtx, PacketBuffer pkb) {
        if (output.isDestroyed()) {
            return false;
        }
        if (vni != pkb.vni) {
            var net = swCtx.getNetwork(pkb.vni);
            if (net == null) {
                return false;
            }
            pkb.network = net;
            pkb.vni = net.vni;
        }
        EthernetPacket ether = new EthernetPacket();
        ether.setDst(remote);
        ether.setSrc(local);
        if (pkb.ipPkt instanceof Ipv4Packet) {
            ether.setType(Consts.ETHER_TYPE_IPv4);
        } else {
            ether.setType(Consts.ETHER_TYPE_IPv6);
        }
        ether.setPacket(pkb.ipPkt);
        pkb.replacePacket(ether);
        return true;
    }

    @Override
    public String toString() {
        return "Fastpath{" +
            "output=" + output +
            ", vni=" + vni +
            ", local=" + local +
            ", remote=" + remote +
            '}';
    }
}
