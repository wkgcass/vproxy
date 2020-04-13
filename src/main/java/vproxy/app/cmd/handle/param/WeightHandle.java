package vproxy.app.cmd.handle.param;

import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;
import vproxy.component.exception.XException;

public class WeightHandle {
    private WeightHandle() {
    }

    public static void check(Command cmd) throws Exception {
        int weight;
        try {
            weight = get(cmd);
        } catch (Exception e) {
            throw new XException("invalid " + Param.w.fullname);
        }
        if (weight < 0)
            throw new XException("invalid " + Param.w.fullname);
    }

    public static int get(Command cmd) {
        if (cmd.args.containsKey(Param.w))
            return Integer.parseInt(cmd.args.get(Param.w));
        else
            return 10;
    }
}
