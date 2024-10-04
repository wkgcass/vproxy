package io.vproxy.app.app.cmd.handle.param;

import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.base.util.Utils;

public class PathHandle {
    private PathHandle() {
    }

    public static String get(Command cmd) throws Exception {
        if (cmd.args.containsKey(Param.path)) {
            return Utils.filename(cmd.args.get(Param.path));
        } else {
            return null;
        }
    }
}
