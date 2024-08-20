package io.vproxy.app.app.cmd.handle.param;

import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;

public class BooleanHandle {
    private BooleanHandle() {
    }

    public static Boolean get(Command cmd) throws Exception {
        String value = cmd.args.get(Param.cors);
        return Boolean.valueOf(value);
    }

}
