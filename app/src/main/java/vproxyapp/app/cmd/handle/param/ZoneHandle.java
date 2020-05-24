package vproxyapp.app.cmd.handle.param;

import vproxyapp.app.cmd.Command;
import vproxyapp.app.cmd.Param;

public class ZoneHandle {
    private ZoneHandle() {
    }

    public static String get(Command cmd) {
        return cmd.args.get(Param.zone);
    }
}
