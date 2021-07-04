package vproxy.app.app.cmd.handle.param;

import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.base.util.exception.XException;

public class HeadroomHandle {
    private HeadroomHandle() {
    }

    public static void check(Command cmd) throws Exception {
        int n;
        try {
            n = Integer.parseInt(cmd.args.get(Param.headroom));
        } catch (Exception e) {
            throw new XException("invalid " + Param.headroom.fullname);
        }
        if (n < 0) {
            throw new XException("invalid " + Param.headroom.fullname + ": cannot be negative");
        }
        if (n > 512) {
            throw new XException("invalid " + Param.headroom.fullname + ": cannot be greater than 512");
        }
    }

    public static int get(Command cmd, int defaultValue) {
        if (!cmd.args.containsKey(Param.headroom)) {
            return defaultValue;
        }
        return Integer.parseInt(cmd.args.get(Param.headroom));
    }
}
