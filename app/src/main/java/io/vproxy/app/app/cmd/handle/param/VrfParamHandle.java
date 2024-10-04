package io.vproxy.app.app.cmd.handle.param;

import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.base.util.exception.XException;

public class VrfParamHandle {
    private VrfParamHandle() {
    }

    public static int get(Command cmd) throws Exception {
        String vrfStr = cmd.args.get(Param.vrf);
        int vrf;
        try {
            vrf = Integer.parseInt(vrfStr);
        } catch (NumberFormatException e) {
            throw new Exception("vrf not a valid integer");
        }
        return vrf;
    }

    public static void check(Command cmd) throws Exception {
        try {
            get(cmd);
        } catch (Exception e) {
            throw new XException("invalid value for " + Param.vrf.fullname + ": " + e.getMessage());
        }
    }
}
