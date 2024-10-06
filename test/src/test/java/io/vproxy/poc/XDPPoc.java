package io.vproxy.poc;

import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Network;
import io.vproxy.component.secure.SecurityGroup;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vswitch.Switch;
import io.vproxy.vswitch.VirtualNetwork;
import io.vproxy.vswitch.iface.XDPIface;
import io.vproxy.xdp.*;

public class XDPPoc {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            Logger.error(LogType.ALERT, "the first argument should be ifname, or use -Difname={...} if you are using gradle to run this poc program");
            return;
        }
        String ifname = args[0];
        boolean csumOffloaded = false;
        for (var i = 1; i < args.length; i++) {
            var arg = args[i];
            if (arg.startsWith("offload=")) {
                var v = arg.substring("offload=".length());
                var split = v.split(",");
                for (var s : split) {
                    s = s.trim();
                    if (s.equals("csum")) {
                        csumOffloaded = true;
                    } else {
                        Logger.error(LogType.ALERT, "unexpected argument " + arg);
                        return;
                    }
                }
            }
        }

        BPFObject bpfobj = BPFObject.loadAndAttachToNic(
            ifname,
            BPFMode.SKB,
            true);
        BPFMap xskMap = bpfobj.getMap(Prebuilt.DEFAULT_XSKS_MAP_NAME);

        EventLoopGroup elg = new EventLoopGroup("elg");
        elg.add("el");
        Switch sw = new Switch("sw0", new IPPort("0.0.0.0:0"), elg,
            60_000, 60_000,
            SecurityGroup.allowAll());
        sw.start();

        VirtualNetwork t = sw.addNetwork(1,
            Network.from("192.168.100.0/24"),
            Network.from("fc00:dead:cafe:1::/64"),
            new Annotations());
        t.addIp(IP.from("fc00:dead:cafe:1::ff01"),
            new MacAddress("00:00:00:00:ff:01"),
            new Annotations());

        UMem umem = sw.addUMem("poc-umem", 64, 32, 32, 4096);
        sw.addXDP(ifname, 1, umem, new XDPIface.XDPParamsBuilder()
            .setQueueId(0)
            .setBPFInfo(new XDPIface.BPFInfo(bpfobj, xskMap))
            .setCsumOffloaded(csumOffloaded)
            .build());
    }
}
