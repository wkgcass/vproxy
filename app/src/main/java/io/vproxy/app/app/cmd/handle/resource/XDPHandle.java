package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Flag;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.app.app.cmd.handle.param.*;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.exception.XException;
import io.vproxy.vswitch.Switch;
import io.vproxy.vswitch.dispatcher.BPFMapKeySelector;
import io.vproxy.vswitch.dispatcher.BPFMapKeySelectors;
import io.vproxy.vswitch.util.SwitchUtils;
import io.vproxy.xdp.BPFMap;
import io.vproxy.xdp.BPFMode;
import io.vproxy.xdp.BPFObject;
import io.vproxy.xdp.UMem;

import java.io.IOException;

public class XDPHandle {
    private XDPHandle() {
    }

    public static void add(Command cmd) throws Exception {
        String nic = cmd.resource.alias;
        BPFObject bpfobj = Application.get().bpfObjectHolder.get(nic);

        String xskMapName = cmd.args.getOrDefault(Param.xskmap, BPFObject.DEFAULT_XSKS_MAP_NAME);
        String macMapName = cmd.args.getOrDefault(Param.macmap, null);
        BPFMap xskMap = bpfobj.getMap(xskMapName);
        BPFMap macMap = null;
        try {
            macMap = bpfobj.getMap(macMapName == null ? BPFObject.DEFAULT_MAC_MAP_NAME : macMapName);
        } catch (IOException e) {
            if (macMapName == null) {
                Logger.warn(LogType.ALERT, "bpfobj " + bpfobj.nic + "(" + bpfobj.filename + ")"
                    + " does not have mac-map " + BPFObject.DEFAULT_MAC_MAP_NAME + ", ignoring ...");
            } else {
                throw e;
            }
        }

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
        if (cmd.args.containsKey(Param.xskmapkeyselector)) {
            keySelector = BPFMapKeySelectorHandle.get(cmd);
        } else {
            keySelector = BPFMapKeySelectors.useQueueId.keySelector.get();
        }
        boolean offload;
        if (cmd.args.containsKey(Param.offload)) {
            offload = OffloadHandle.get(cmd);
        } else {
            offload = false;
        }
        // check offload
        if (offload) {
            if (macMap == null) {
                throw new XException("offload is true but mac-map is not provided");
            }
        }

        sw.addXDP(nic, xskMap, macMap, umem, queueId,
            rxRingSize, txRingSize, mode, zeroCopy, busyPollBudget, rxGenChecksum,
            vni, keySelector, offload);
    }
}
