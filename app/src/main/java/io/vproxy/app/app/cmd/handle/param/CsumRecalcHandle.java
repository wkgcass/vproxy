package io.vproxy.app.app.cmd.handle.param;

import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.base.util.exception.XException;
import io.vproxy.vswitch.util.CSumRecalcType;

public class CsumRecalcHandle {
    private CsumRecalcHandle() {
    }

    public static CSumRecalcType get(Command cmd) throws Exception {
        String csumRecalc = cmd.args.get(Param.csumrecalc);
        if (csumRecalc.equals("all")) {
            return CSumRecalcType.all;
        } else if (csumRecalc.equals("none")) {
            return CSumRecalcType.none;
        } else {
            throw new Exception("all nor none");
        }
    }

    public static void check(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.csumrecalc)) {
            throw new Exception("missing argument " + Param.csumrecalc.fullname);
        }
        try {
            get(cmd);
        } catch (Exception e) {
            throw new XException("invalid value for " + Param.csumrecalc.fullname + ": " + e.getMessage());
        }
    }
}
