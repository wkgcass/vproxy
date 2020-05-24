package vproxyapp.app.cmd.handle.param;

import vproxyapp.app.cmd.Command;
import vproxyapp.app.cmd.Param;
import vproxybase.util.exception.XException;

public class SecGRDefaultHandle {
    private SecGRDefaultHandle() {
    }

    public static void check(Command cmd) throws Exception {
        try {
            get(cmd);
        } catch (Exception e) {
            throw new XException("invalid format for " + Param.secgrdefault.fullname);
        }
    }

    public static boolean get(Command cmd) {
        String dft = cmd.args.get(Param.secgrdefault);
        if (dft.equals("allow"))
            return true;
        if (dft.equals("deny"))
            return false;
        throw new IllegalArgumentException();
    }
}
