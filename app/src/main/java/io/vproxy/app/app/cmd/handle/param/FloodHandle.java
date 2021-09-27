package io.vproxy.app.app.cmd.handle.param;

import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.base.util.exception.XException;

public class FloodHandle {
    private FloodHandle() {
    }

    public static boolean get(Command cmd) throws Exception {
        String flood = cmd.args.get(Param.flood);
        if (flood.equals("allow")) {
            return true;
        } else if (flood.equals("deny")) {
            return false;
        } else {
            throw new Exception("allow nor deny");
        }
    }

    public static void check(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.flood)) {
            throw new Exception("missing argument " + Param.flood.fullname);
        }
        try {
            get(cmd);
        } catch (Exception e) {
            throw new XException("invalid value for " + Param.flood.fullname + ": " + e.getMessage());
        }
    }
}
