package vproxy.app.cmd.handle.param;

import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;
import vproxy.component.exception.XException;

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
