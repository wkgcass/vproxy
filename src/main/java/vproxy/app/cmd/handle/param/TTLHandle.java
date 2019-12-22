package vproxy.app.cmd.handle.param;

import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;

public class TTLHandle {
    private TTLHandle() {
    }

    public static void check(Command cmd) throws Exception {
        try {
            get(cmd);
        } catch (Exception e) {
            throw new Exception("invalid " + Param.ttl.fullname);
        }
    }

    public static int get(Command cmd) {
        return Integer.parseInt(cmd.args.get(Param.ttl));
    }
}
