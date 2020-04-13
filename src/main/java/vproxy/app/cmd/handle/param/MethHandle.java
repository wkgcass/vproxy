package vproxy.app.cmd.handle.param;

import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;
import vproxy.component.exception.XException;
import vproxy.component.svrgroup.Method;

public class MethHandle {
    private MethHandle() {
    }

    public static Method get(Command cmd) throws Exception {
        String meth = cmd.args.get(Param.meth);
        try {
            return Method.valueOf(meth);
        } catch (IllegalArgumentException e) {
            throw new XException("invalid " + Param.meth.fullname);
        }
    }
}
