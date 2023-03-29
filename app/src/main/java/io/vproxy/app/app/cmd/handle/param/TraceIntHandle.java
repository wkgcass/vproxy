package io.vproxy.app.app.cmd.handle.param;

import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.base.util.exception.XException;

public class TraceIntHandle {
    private TraceIntHandle() {
    }

    public static int get(Command cmd) throws XException {
        int trace = Integer.parseInt(cmd.args.get(Param.trace));
        if (trace < 0)
            throw new XException("invalid " + Param.trace.fullname + ": should not be negative");
        return trace;
    }

    public static void check(Command cmd) throws XException {
        try {
            get(cmd);
        } catch (NumberFormatException e) {
            throw new XException("invalid " + Param.trace.fullname + ": not a number");
        }
    }
}
