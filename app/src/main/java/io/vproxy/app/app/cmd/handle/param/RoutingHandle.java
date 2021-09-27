package io.vproxy.app.app.cmd.handle.param;

import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;

public class RoutingHandle {
    private RoutingHandle() {
    }

    public static void check(Command cmd) throws Exception {
        get(cmd);
    }

    public static boolean get(Command cmd) throws Exception {
        String str = cmd.args.get(Param.routing);
        if (str == null) {
            throw new Exception("missing param " + Param.routing.fullname);
        }
        if (str.equals("on")) {
            return true;
        } else if (str.equals("off")) {
            return false;
        } else {
            throw new Exception("invalid value for " + Param.routing.fullname + ": " + str);
        }
    }
}
