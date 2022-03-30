package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.Resource;
import io.vproxy.base.connection.Protocol;
import io.vproxy.base.util.display.TableBuilder;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.conntrack.tcp.TcpState;
import io.vproxy.vswitch.Switch;
import io.vproxy.vswitch.VirtualNetwork;

import java.util.ArrayList;
import java.util.List;

public class ConntrackHandle {
    private ConntrackHandle() {
    }

    public static int count(Resource parent) throws Exception {
        var network = getNetwork(parent);
        int total = 0;
        total += network.conntrack.countTcpEntries();
        total += network.conntrack.countUdpEntries();
        total += network.conntrack.countTcpListenEntry();
        total += network.conntrack.countUdpListenEntry();
        return total;
    }

    private static VirtualNetwork getNetwork(Resource parent) throws Exception {
        String vpcStr = parent.alias;
        int vpc = Integer.parseInt(vpcStr);
        Switch sw = Application.get().switchHolder.get(parent.parentResource.alias);
        var networks = sw.getNetworks().values();

        VirtualNetwork network = null;
        for (var net : networks) {
            if (net.vni == vpc) {
                network = net;
                break;
            }
        }
        if (network == null) {
            throw new NotFoundException("vpc", vpcStr);
        }
        return network;
    }

    public static List<ConntrackEntry> list(Resource parent) throws Exception {
        var network = getNetwork(parent);
        var result = new ArrayList<ConntrackEntry>();

        for (var tcpLsn : network.conntrack.listTcpListenEntries()) {
            result.add(new ConntrackEntry(
                Protocol.TCP, TcpState.LISTEN,
                new IPPort("0.0.0.0:0"), tcpLsn.listening
            ));
        }
        for (var udpLsn : network.conntrack.listUdpListenEntries()) {
            result.add(new ConntrackEntry(
                Protocol.UDP, TcpState.LISTEN,
                new IPPort("0.0.0.0:0"), udpLsn.listening
            ));
        }
        for (var tcp : network.conntrack.listTcpEntries()) {
            var nat = tcp.getNat();
            if (nat == null) {
                result.add(new ConntrackEntry(
                    Protocol.TCP, tcp.getState(), tcp.source, tcp.destination
                ));
            } else {
                var another = nat._1 == tcp ? nat._2 : nat._1;
                result.add(new ConntrackEntry(
                    Protocol.TCP, nat.getState(), tcp.source, tcp.destination,
                    new NatRecord(another.source, another.destination, nat.getTTL())
                ));
            }
        }
        for (var udp : network.conntrack.listUdpEntries()) {
            result.add(new ConntrackEntry(
                Protocol.UDP, udp.remote, udp.local
            ));
        }

        return result;
    }

    public static class ConntrackEntry {
        public final Protocol protocol;
        public final TcpState state;
        public final IPPort remote;
        public final IPPort local;

        public final NatRecord nat;

        public ConntrackEntry(Protocol protocol, TcpState state, IPPort remote, IPPort local) {
            this(protocol, state, remote, local, null);
        }

        public ConntrackEntry(Protocol protocol, IPPort remote, IPPort local) {
            this(protocol, null, remote, local);
        }

        public ConntrackEntry(Protocol protocol, TcpState state, IPPort remote, IPPort local,
                              NatRecord nat) {
            this.protocol = protocol;
            this.state = state;
            this.remote = remote;
            this.local = local;
            this.nat = nat;
        }

        public ConntrackEntry(Protocol protocol, IPPort remote, IPPort local,
                              NatRecord nat) {
            this(protocol, null, remote, local, nat);
        }

        public void buildTable(TableBuilder tb) {
            var tr = tb.tr();
            tr.td(protocol.name())
                .td(state == null ? "" : state.name())
                .td("remote=" + remote.formatToIPPortString())
                .td("local=" + local.formatToIPPortString());
            if (nat != null) {
                tr.td("--nat->")
                    .td("local=" + nat.local.formatToIPPortString())
                    .td("remote=" + nat.remote.formatToIPPortString())
                    .td("TTL:" + (nat.ttl / 1000));
            }
        }
    }

    public static class NatRecord {
        public final IPPort remote;
        public final IPPort local;
        public final long ttl;

        public NatRecord(IPPort remote, IPPort local, long ttl) {
            this.remote = remote;
            this.local = local;
            this.ttl = ttl;
        }
    }
}
