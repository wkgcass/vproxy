package vproxy.app.cmd.handle.param;

import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;

public class WeightHandle {
    private WeightHandle() {
    }

    public static void check(Command cmd) throws Exception {
        int weight;
        try {
            weight = get(cmd);
        } catch (Exception e) {
            throw new Exception("invalid " + Param.w.fullname);
        }
        if (weight < 0)
            throw new Exception("invalid " + Param.w.fullname);
    }

    public static int get(Command cmd) {
        return Integer.parseInt(cmd.args.get(Param.w));
    }
}
