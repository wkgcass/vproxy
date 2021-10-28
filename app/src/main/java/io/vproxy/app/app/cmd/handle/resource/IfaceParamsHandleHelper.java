package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.app.app.cmd.handle.param.CsumRecalcHandle;
import io.vproxy.app.app.cmd.handle.param.FloodHandle;
import io.vproxy.app.app.cmd.handle.param.MTUHandle;
import io.vproxy.vswitch.iface.IfaceParams;

public class IfaceParamsHandleHelper {
    private IfaceParamsHandleHelper() {
    }

    public static void update(Command cmd, IfaceParams params) throws Exception {
        if (cmd.args.containsKey(Param.mtu)) {
            params.setBaseMTU(MTUHandle.get(cmd));
        }
        if (cmd.args.containsKey(Param.flood)) {
            params.setFloodAllowed(FloodHandle.get(cmd));
        }
        if (cmd.args.containsKey(Param.csumrecalc)) {
            params.setCSumRecalc(CsumRecalcHandle.get(cmd));
        }
    }
}
