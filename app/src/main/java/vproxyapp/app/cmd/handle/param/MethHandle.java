package vproxyapp.app.cmd.handle.param;

import vproxyapp.app.cmd.Command;
import vproxyapp.app.cmd.Param;
import vproxybase.component.svrgroup.Method;
import vproxybase.util.exception.XException;

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
