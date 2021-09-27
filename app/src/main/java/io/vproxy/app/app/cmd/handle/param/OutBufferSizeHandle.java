package vproxy.app.app.cmd.handle.param;

import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.base.util.exception.XException;

public class OutBufferSizeHandle {
    private OutBufferSizeHandle() {
    }

    public static void check(Command cmd) throws Exception {
        try {
            Integer.parseInt(cmd.args.get(Param.outbuffersize));
        } catch (Exception e) {
            throw new XException("invalid " + Param.outbuffersize.fullname);
        }
    }

    public static int get(Command cmd, int defaultValue) {
        if (!cmd.args.containsKey(Param.outbuffersize)) {
            return defaultValue;
        }
        return Integer.parseInt(cmd.args.get(Param.outbuffersize));
    }
}
