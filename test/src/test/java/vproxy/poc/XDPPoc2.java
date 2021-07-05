package vproxy.poc;

import vproxy.base.component.elgroup.EventLoopGroup;
import vproxy.base.util.Annotations;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.base.util.Network;
import vproxy.component.secure.SecurityGroup;
import vproxy.vfd.IP;
import vproxy.vfd.IPPort;
import vproxy.vfd.MacAddress;
import vproxy.vswitch.Switch;
import vproxy.vswitch.Table;
import vproxy.vswitch.dispatcher.BPFMapKeySelectors;
import vproxy.xdp.BPFMap;
import vproxy.xdp.BPFMode;
import vproxy.xdp.BPFObject;
import vproxy.xdp.UMem;

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

        Table t = sw.addTable(1,
            new Network("192.168.100.0/24"),
            new Network("fc00:dead:cafe:1::/64"),
            new Annotations());
        t.addIp(IP.from("fc00:dead:cafe:1::ff01"),
            new MacAddress("00:00:00:00:ff:01"),
            new Annotations());

        UMem umem = sw.addUMem("poc-umem", 5, 4, 4, 4096, 0);
        sw.addXDP("poc-xdp",
            ifname, bpfMap, umem, 0, 4, 4, BPFMode.SKB, false, 1,
            BPFMapKeySelectors.useQueueId.keySelector.get());
    }
}
