package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Flag;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.app.app.cmd.handle.param.*;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.vswitch.Switch;
import io.vproxy.vswitch.iface.XDPIface;
import io.vproxy.vswitch.util.SwitchUtils;
import io.vproxy.xdp.*;

public class XDPHandle {
    private XDPHandle() {
    }

    public static void add(Command cmd) throws Exception {
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        UMem umem = UMemHandle.get(cmd.args.get(Param.umem), sw.alias);

        int queueId = QueueHandle.get(cmd);
        int rxRingSize = RingSizeHandle.get(cmd, Param.rxringsize, SwitchUtils.RX_TX_CHUNKS);
        int txRingSize = RingSizeHandle.get(cmd, Param.txringsize, SwitchUtils.RX_TX_CHUNKS);
        boolean zeroCopy = cmd.flags.contains(Flag.zerocopy);
        int busyPollBudget = 0;
        if (cmd.args.containsKey(Param.busypoll)) {
            busyPollBudget = BusyPollHandle.get(cmd);
        }
        boolean rxGenChecksum = cmd.flags.contains(Flag.rxgencsum);
        int vni = VniHandle.get(cmd);
        boolean offload = cmd.flags.contains(Flag.offload);

        var nic = cmd.resource.alias;
        var mode = BPFModeHandle.get(cmd, BPFMode.SKB);
        var createResult = SwitchUtils.createBPFObjectWithReusedMaps(
            sw, vni, (reuseMap) -> BPFObject.loadAndAttachToNic(nic, reuseMap, mode, true)
        );
        var bpfobj = createResult.object();

        try {
            var xskMap = bpfobj.getMap(Prebuilt.DEFAULT_XSKS_MAP_NAME);
            var macMap = createResult.map();
            var srcmac2countMap = bpfobj.getMap(Prebuilt.DEFAULT_SRC_MAC_TO_COUNT_MAP_NAME);

            sw.addXDP(nic, vni, umem, new XDPIface.XDPParams(
                queueId, rxRingSize, txRingSize, mode, zeroCopy, busyPollBudget, rxGenChecksum, offload,
                new XDPIface.BPFInfo(bpfobj, xskMap, macMap, srcmac2countMap, createResult.groupName())));
        } catch (Exception e) {
            Logger.error(LogType.SYS_ERROR, "failed to create xdp interface " + nic, e);
            createResult.release(true);
        }
    }
}
