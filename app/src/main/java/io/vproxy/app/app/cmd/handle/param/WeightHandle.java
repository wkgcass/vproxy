package vproxy.app.app.cmd.handle.param;

import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.base.util.exception.XException;

public class WeightHandle {
    private WeightHandle() {
    }

    public static void check(Command cmd) throws Exception {
        int weight;
        try {
            weight = get(cmd);
        } catch (Exception e) {
            throw new XException("invalid " + Param.weight.fullname);
        }
        if (weight < 0)
            throw new XException("invalid " + Param.weight.fullname);
    }

    public static int get(Command cmd) {
        if (cmd.args.containsKey(Param.weight))
            return Integer.parseInt(cmd.args.get(Param.weight));
        else
            return 10;
    }
}
