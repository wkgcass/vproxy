package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.app.app.cmd.Resource;
import io.vproxy.app.app.cmd.ResourceType;
import io.vproxy.app.app.cmd.handle.param.FrameSizeHandle;
import io.vproxy.app.app.cmd.handle.param.RingSizeHandle;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.vswitch.Switch;
import io.vproxy.vswitch.util.SwitchUtils;
import io.vproxy.xdp.UMem;

import java.util.List;
import java.util.stream.Collectors;

public class UMemHandle {
    private UMemHandle() {
    }

    public static UMem get(String alias, String swAlias) throws NotFoundException {
        Switch sw = Application.get().switchHolder.get(swAlias);
        var ls = sw.getUMems();
        for (var umem : ls) {
            if (umem.alias.equals(alias)) {
                return umem;
            }
        }
        throw new NotFoundException(ResourceType.umem.fullname, alias);
    }

    public static void add(Command cmd) throws Exception {
        int chunksSize = RingSizeHandle.get(cmd, Param.chunks, SwitchUtils.DEFAULT_UMEM_CHUNKS);
        int fillRingSize = RingSizeHandle.get(cmd, Param.fillringsize, SwitchUtils.DEFAULT_RX_TX_CHUNKS);
        int compRingSize = RingSizeHandle.get(cmd, Param.compringsize, SwitchUtils.DEFAULT_RX_TX_CHUNKS);
        int frameSize = FrameSizeHandle.get(cmd, SwitchUtils.TOTAL_RCV_BUF_LEN / 2);

        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.addUMem(cmd.resource.alias, chunksSize, fillRingSize, compRingSize, frameSize);
    }

    public static List<String> names(Resource resource) throws Exception {
        return list(resource).stream().map(umem -> umem.alias).collect(Collectors.toList());
    }

    public static List<UMem> list(Resource resource) throws Exception {
        Switch sw = Application.get().switchHolder.get(resource.alias);
        return sw.getUMems();
    }

    public static void remove(Command cmd) throws Exception {
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.delUMem(cmd.resource.alias);
    }
}
