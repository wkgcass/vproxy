package io.vproxy.app.app.cmd.handle.param;

import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.base.util.exception.XException;

public class RingSizeHandle {
    private RingSizeHandle() {
    }

    public static void check(Command cmd, Param param) throws Exception {
        int n;
        try {
            n = Integer.parseInt(cmd.args.get(param));
        } catch (Exception e) {
            throw new XException("invalid " + param.fullname);
        }
        if (n < 0) {
            throw new XException("invalid " + param.fullname + ": cannot be negative");
        }
    }

    public static int get(Command cmd, Param param, int defaultValue) {
        if (!cmd.args.containsKey(param)) {
            return defaultValue;
        }
        return Integer.parseInt(cmd.args.get(param));
    }
}
