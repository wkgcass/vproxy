package vproxy.app.app.cmd.handle.param;

import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.vfd.MacAddress;

public class MacHandle {
    private MacHandle() {
    }

    public static void check(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.mac)) {
            throw new Exception("missing argument: " + Param.mac.fullname);
        }
        get(cmd);
    }

    public static MacAddress get(Command cmd) throws Exception {
        String value = cmd.args.get(Param.mac);
        try {
            return new MacAddress(value);
        } catch (IllegalArgumentException e) {
            throw new Exception("invalid mac address: " + value);
        }
    }
}
