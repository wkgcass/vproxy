package io.vproxy.app.app.cmd.handle.param;

import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.base.util.exception.XException;

public class OffloadHandle {
    private OffloadHandle() {
    }

    public static boolean get(Command cmd) throws Exception {
        String offload = cmd.args.get(Param.offload);
        if (offload.equals("true")) {
            return true;
        } else if (offload.equals("false")) {
            return false;
        } else {
            throw new Exception("true nor false");
        }
    }

    public static void check(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.offload)) {
            throw new Exception("missing argument " + Param.offload.fullname);
        }
        try {
            get(cmd);
        } catch (Exception e) {
            throw new XException("invalid value for " + Param.offload.fullname + ": " + e.getMessage());
        }
    }
}
