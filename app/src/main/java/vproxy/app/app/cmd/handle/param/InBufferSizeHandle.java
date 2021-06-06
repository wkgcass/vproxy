package vproxy.app.app.cmd.handle.param;

import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.base.util.exception.XException;

public class InBufferSizeHandle {
    private InBufferSizeHandle() {
    }

    public static void check(Command cmd) throws Exception {
        try {
            get(cmd);
        } catch (Exception e) {
            throw new XException("invalid " + Param.inbuffersize.fullname);
        }
    }

    public static int get(Command cmd) {
        return Integer.parseInt(cmd.args.get(Param.inbuffersize));
    }
}
