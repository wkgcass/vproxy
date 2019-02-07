package net.cassite.vproxy.app.cmd.handle.param;

import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.app.cmd.Param;
import net.cassite.vproxy.component.exception.XException;

public class PortHandle {
    private PortHandle() {
    }

    public static void check(Command cmd) throws XException {
        int port;
        try {
            port = get(cmd);
        } catch (Exception e) {
            throw new XException("invalid " + Param.port.fullname);
        }
        if (port < 1 || port > 65535) {
            throw new XException("invalid " + Param.port.fullname);
        }
    }

    public static int get(Command cmd) {
        return Integer.parseInt(cmd.args.get(Param.port));
    }
}
