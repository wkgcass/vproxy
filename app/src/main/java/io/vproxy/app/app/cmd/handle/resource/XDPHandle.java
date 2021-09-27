package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Flag;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.app.app.cmd.handle.param.*;
import io.vproxy.vswitch.Switch;
import io.vproxy.vswitch.dispatcher.BPFMapKeySelector;
import io.vproxy.vswitch.dispatcher.BPFMapKeySelectors;
import io.vproxy.vswitch.util.SwitchUtils;
import io.vproxy.xdp.BPFMap;
import io.vproxy.xdp.BPFMode;
import io.vproxy.xdp.BPFObject;
import io.vproxy.xdp.UMem;

public class XDPHandle {
    private XDPHandle() {
    }

    public static void add(Command cmd) throws Exception {
        String nic = cmd.resource.alias;
        BPFObject bpfobj = Application.get().bpfObjectHolder.get(nic);

        String mapName = cmd.args.getOrDefault(Param.bpfmap, BPFObject.DEFAULT_XSKS_MAP_NAME);
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
        boolean rxGenChecksum = cmd.flags.contains(Flag.rxgencsum);
        int vni = VniHandle.get(cmd);
        BPFMapKeySelector keySelector;
        if (cmd.args.containsKey(Param.bpfmapkeyselector)) {
            keySelector = BPFMapKeySelectorHandle.get(cmd);
        } else {
            keySelector = BPFMapKeySelectors.useQueueId.keySelector.get();
        }

        sw.addXDP(nic, bpfMap, umem, queueId,
            rxRingSize, txRingSize, mode, zeroCopy, busyPollBudget, rxGenChecksum,
            vni, keySelector);
    }
}
