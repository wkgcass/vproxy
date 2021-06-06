package vproxy.app.app.cmd.handle.param;

import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;

public class ServiceHandle {
    private ServiceHandle() {
    }

    public static String get(Command cmd) {
        return cmd.args.get(Param.service);
    }
}
