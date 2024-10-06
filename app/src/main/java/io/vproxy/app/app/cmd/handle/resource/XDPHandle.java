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
        int rxRingSize = RingSizeHandle.get(cmd, Param.rxringsize, SwitchUtils.DEFAULT_RX_TX_CHUNKS);
        int txRingSize = RingSizeHandle.get(cmd, Param.txringsize, SwitchUtils.DEFAULT_RX_TX_CHUNKS);
        boolean zeroCopy = cmd.flags.contains(Flag.zerocopy);
        int busyPollBudget = 0;
        if (cmd.args.containsKey(Param.busypoll)) {
            busyPollBudget = BusyPollHandle.get(cmd);
        }
        boolean rxGenChecksum = cmd.flags.contains(Flag.rxgencsum);
        int vrf = VrfParamHandle.get(cmd);
        boolean pktswOffloaded = OffloadHandle.isPacketSwitchingOffloaded(cmd);
        boolean csumOffloaded = OffloadHandle.isChecksumOffloaded(cmd);

        var nic = cmd.resource.alias;
        var mode = BPFModeHandle.get(cmd, BPFMode.SKB);
        var createResult = SwitchUtils.createBPFObjectWithReusedMaps(
            sw, vrf, (reuseMap) -> BPFObject.loadAndAttachToNic(nic, reuseMap, mode, true)
        );
        var bpfobj = createResult.object();

        try {
            var xskMap = bpfobj.getMap(Prebuilt.DEFAULT_XSKS_MAP_NAME);
            var mac2portMap = createResult.mac2portMap();
            var port2devMap = createResult.port2devMap();
            var srcmac2countMap = bpfobj.getMap(Prebuilt.DEFAULT_SRC_MAC_TO_COUNT_MAP_NAME);
            var passmacMap = bpfobj.getMap(Prebuilt.DEFAULT_PASS_MAC_MAP_NAME);

            var bpfInfo = new XDPIface.BPFInfo(bpfobj, xskMap, mac2portMap, port2devMap, srcmac2countMap, passmacMap, createResult.groupName());

            sw.addXDP(nic, vrf, umem, new XDPIface.XDPParamsBuilder()
                .setQueueId(queueId)
                .setBPFInfo(bpfInfo)
                .setRxRingSize(rxRingSize)
                .setTxRingSize(txRingSize)
                .setMode(mode)
                .setZeroCopy(zeroCopy)
                .setBusyPollBudget(busyPollBudget)
                .setRxGenChecksum(rxGenChecksum)
                .setPktswOffloaded(pktswOffloaded)
                .setCsumOffloaded(csumOffloaded)
                .build()
            );
        } catch (Exception e) {
            Logger.error(LogType.SYS_ERROR, "failed to create xdp interface " + nic, e);
            createResult.release(true);
        }
    }
}
