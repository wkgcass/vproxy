package vproxy.app.app.cmd.handle.param;

import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.base.util.exception.XException;

public class FrameSizeHandle {
    private FrameSizeHandle() {
    }

    public static void check(Command cmd) throws Exception {
        int n;
        try {
            n = Integer.parseInt(cmd.args.get(Param.framesize));
        } catch (Exception e) {
            throw new XException("invalid " + Param.framesize.fullname);
        }
        if (n != 2048 && n != 4096) {
            throw new XException("invalid " + Param.framesize.fullname + ": can only be 2048 or 4096");
        }
    }

    public static int get(Command cmd, int defaultValue) {
        if (!cmd.args.containsKey(Param.framesize)) {
            return defaultValue;
        }
        return Integer.parseInt(cmd.args.get(Param.framesize));
    }
}
