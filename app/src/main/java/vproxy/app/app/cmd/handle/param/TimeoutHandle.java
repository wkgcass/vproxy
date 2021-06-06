package vproxy.app.app.cmd.handle.param;

import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.base.util.exception.XException;

public class TimeoutHandle {
    private TimeoutHandle() {
    }

    public static int get(Command cmd) throws Exception {
        return get(cmd, Param.timeout);
    }

    public static int get(Command cmd, Param param) throws XException {
        int timeout = Integer.parseInt(cmd.args.get(param));
        if (timeout < 0)
            throw new XException("invalid " + param.fullname);
        return timeout;
    }

    public static void check(Command cmd) throws XException {
        check(cmd, Param.timeout);
    }

    public static void check(Command cmd, Param param) throws XException {
        get(cmd, param);
    }
}
