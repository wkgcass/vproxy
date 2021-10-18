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
import io.vproxy.vswitch.dispatcher.BPFMapKeySelectors;
import io.vproxy.xdp.BPFMap;
import io.vproxy.xdp.BPFMode;
import io.vproxy.xdp.BPFObject;
import io.vproxy.xdp.UMem;

public class XDPPoc2 {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            Logger.error(LogType.ALERT, "the first argument should be ifname, or use -Difname={...} if you are using gradle to run this poc program");
            return;
        }
        String ifname = args[0];

        BPFObject bpfobj = BPFObject.loadAndAttachToNic(
            "./base/src/main/c/xdp/sample_kern.o",
            "xdp_sock",
            ifname,
            BPFMode.SKB,
            true);
        BPFMap bpfMap = bpfobj.getMap("xsks_map");

        EventLoopGroup elg = new EventLoopGroup("elg");
        elg.add("el");
        Switch sw = new Switch("sw0", new IPPort("127.30.30.30:30"), elg,
            1000, 1000,
            SecurityGroup.allowAll(),
            1500, true);
        sw.start();

        VirtualNetwork t = sw.addNetwork(1,
            Network.from("192.168.100.0/24"),
            Network.from("fc00:dead:cafe:1::/64"),
            new Annotations());
        t.addIp(IP.from("fc00:dead:cafe:1::ff01"),
            new MacAddress("00:00:00:00:ff:01"),
            new Annotations());

        UMem umem = sw.addUMem("poc-umem", 64, 32, 32, 4096);
        sw.addXDP(
            ifname, bpfMap, umem, 0, 32, 32, BPFMode.SKB, false, 0, true,
            1, BPFMapKeySelectors.useQueueId.keySelector.get());
    }
}
