package vproxy.app.cmd.handle.param;

import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;

public class ZoneHandle {
    private ZoneHandle() {
    }

    public static String get(Command cmd) {
        return cmd.args.get(Param.zone);
    }
}
