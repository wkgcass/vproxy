package vproxyapp.app.cmd.handle.param;

import vproxyapp.app.cmd.Command;
import vproxyapp.app.cmd.Param;
import vproxybase.util.exception.XException;

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
