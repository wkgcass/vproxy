package net.cassite.vproxy.app.cmd.handle.param;

import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.app.cmd.Param;

public class ServiceHandle {
    private ServiceHandle() {
    }

    public static String get(Command cmd) {
        return cmd.args.get(Param.service);
    }
}
