package net.cassite.vproxy.app.cmd.handle.param;

import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.app.cmd.Param;
import net.cassite.vproxy.component.svrgroup.Method;

public class MethHandle {
    private MethHandle() {
    }

    public static Method get(Command cmd) throws Exception {
        String meth = cmd.args.get(Param.meth);
        try {
            return Method.valueOf(meth);
        } catch (IllegalArgumentException e) {
            throw new Exception("invalid " + Param.meth.fullname);
        }
    }
}
