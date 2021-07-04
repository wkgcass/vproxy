package vproxy.app.app.cmd.handle.param;

import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.base.util.exception.XException;

public class QueueHandle {
    private QueueHandle() {
    }

    public static void check(Command cmd) throws XException {
        int q;
        try {
            q = get(cmd);
        } catch (Throwable t) {
            throw new XException("invalid " + Param.queue.fullname + ": not an integer");
        }
        if (q < 0) {
            throw new XException("invalid " + Param.queue.fullname + ": queue id cannot be negative");
        }
    }

    public static int get(Command cmd) {
        return Integer.parseInt(cmd.args.get(Param.queue));
    }
}
