package vproxy.app.app.cmd.handle.param;

import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.base.util.exception.XException;

public class MTUHandle {
    private MTUHandle() {
    }

    public static int get(Command cmd) throws Exception {
        String mtuStr = cmd.args.get(Param.mtu);
        int mtu;
        try {
            mtu = Integer.parseInt(mtuStr);
        } catch (NumberFormatException e) {
            throw new Exception("mtu not a valid integer");
        }
        if (mtu < 68) {
            throw new Exception("mtu too small, must >= 68");
        }
        if (mtu > 65535) {
            throw new Exception("mtu too big, must <= 65535");
        }
        return mtu;
    }

    public static void check(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.mtu))
            throw new Exception("missing argument " + Param.mtu.fullname);

        try {
            get(cmd);
        } catch (Exception e) {
            throw new XException("invalid value for " + Param.mtu.fullname + ": " + e.getMessage());
        }
    }
}
