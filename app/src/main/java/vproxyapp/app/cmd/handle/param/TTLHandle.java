package vproxyapp.app.cmd.handle.param;

import vproxyapp.app.cmd.Command;
import vproxyapp.app.cmd.Param;
import vproxybase.util.exception.XException;

public class TTLHandle {
    private TTLHandle() {
    }

    public static void check(Command cmd) throws Exception {
        try {
            get(cmd);
        } catch (Exception e) {
            throw new XException("invalid " + Param.ttl.fullname);
        }
    }

    public static int get(Command cmd) {
        return Integer.parseInt(cmd.args.get(Param.ttl));
    }
}
