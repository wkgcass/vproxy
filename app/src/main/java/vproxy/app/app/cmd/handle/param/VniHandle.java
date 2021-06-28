package vproxy.app.app.cmd.handle.param;

import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.base.util.exception.XException;

public class VniHandle {
    private VniHandle() {
    }

    public static int get(Command cmd) throws Exception {
        String vniStr = cmd.args.get(Param.vni);
        int vni;
        try {
            vni = Integer.parseInt(vniStr);
        } catch (NumberFormatException e) {
            throw new Exception("vni not a valid integer");
        }
        return vni;
    }

    public static void check(Command cmd) throws Exception {
        try {
            get(cmd);
        } catch (Exception e) {
            throw new XException("invalid value for " + Param.vni.fullname + ": " + e.getMessage());
        }
    }
}
