package net.cassite.vproxy.app.cmd.handle.param;

import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.app.cmd.Param;

public class ZoneHandle {
    private ZoneHandle() {
    }

    public static String get(Command cmd) {
        return cmd.args.get(Param.zone);
    }
}
