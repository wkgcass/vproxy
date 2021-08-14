package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Flag;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.handle.param.*;
import vproxy.vswitch.Switch;
import vproxy.vswitch.dispatcher.BPFMapKeySelector;
import vproxy.vswitch.dispatcher.BPFMapKeySelectors;
import vproxy.vswitch.util.SwitchUtils;
import vproxy.xdp.BPFMap;
import vproxy.xdp.BPFMode;
import vproxy.xdp.BPFObject;
import vproxy.xdp.UMem;

public class XDPHandle {
    private XDPHandle() {
    }

    public static void add(Command cmd) throws Exception {
        String nic = cmd.resource.alias;
        BPFObject bpfobj = Application.get().bpfObjectHolder.get(nic);

        String mapName = cmd.args.getOrDefault(Param.bpfmap, "xsks_map");
        BPFMap bpfMap = bpfobj.getMap(mapName);

        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);

        String umemName = cmd.args.get(Param.umem);
        UMem umem = UMemHandle.get(umemName, sw.alias);

        int queueId = QueueHandle.get(cmd);
        int rxRingSize = RingSizeHandle.get(cmd, Param.rxringsize, SwitchUtils.RX_TX_CHUNKS);
        int txRingSize = RingSizeHandle.get(cmd, Param.txringsize, SwitchUtils.RX_TX_CHUNKS);
        BPFMode mode = BPFModeHandle.get(cmd, BPFMode.SKB);
        boolean zeroCopy = cmd.flags.contains(Flag.zerocopy);
        int busyPollBudget = 0;
        if (cmd.args.containsKey(Param.busypoll)) {
            busyPollBudget = BusyPollHandle.get(cmd);
        }
        int vni = VniHandle.get(cmd);
        BPFMapKeySelector keySelector;
        if (cmd.args.containsKey(Param.bpfmapkeyselector)) {
            keySelector = BPFMapKeySelectorHandle.get(cmd);
        } else {
            keySelector = BPFMapKeySelectors.useQueueId.keySelector.get();
        }

        sw.addXDP(nic, bpfMap, umem, queueId,
            rxRingSize, txRingSize, mode, zeroCopy, busyPollBudget,
            vni, keySelector);
    }

    public static void remove(Command cmd) throws Exception {
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.delXDP(cmd.resource.alias);
    }
}
