package io.vproxy.app.app.cmd.handle.param;

import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.base.component.svrgroup.Method;
import io.vproxy.base.util.exception.XException;

public class MethHandle {
    private MethHandle() {
    }

    public static Method get(Command cmd, String defaultValue) throws Exception {
        if (!cmd.args.containsKey(Param.meth)) {
            return Method.valueOf(defaultValue);
        }
        String meth = cmd.args.get(Param.meth);
        try {
            return Method.valueOf(meth);
        } catch (IllegalArgumentException e) {
            throw new XException("invalid " + Param.meth.fullname);
        }
    }
}
