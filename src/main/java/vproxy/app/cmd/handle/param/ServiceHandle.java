package vproxy.app.cmd.handle.param;

import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;

public class ServiceHandle {
    private ServiceHandle() {
    }

    public static String get(Command cmd) {
        return cmd.args.get(Param.service);
    }
}
