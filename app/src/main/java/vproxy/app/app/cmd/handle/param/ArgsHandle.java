package vproxy.app.app.cmd.handle.param;

import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;

public class ArgsHandle {
    private ArgsHandle() {
    }

    public static String[] get(Command cmd) throws Exception {
        String args = cmd.args.get(Param.args);
        return args.split(",");
    }
}
