package io.vproxy.app.app.cmd.handle.param;

import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.base.util.exception.XException;
import io.vproxy.xdp.BPFMode;

public class BPFModeHandle {
    private BPFModeHandle() {
    }

    public static void check(Command cmd) throws XException {
        try {
            BPFMode.valueOf(cmd.args.get(Param.mode));
        } catch (IllegalArgumentException e) {
            throw new XException("unknown bpf mode: " + cmd.args.get(Param.mode));
        }
    }

    public static BPFMode get(Command cmd, BPFMode defaultValue) {
        if (!cmd.args.containsKey(Param.mode)) {
            return defaultValue;
        }
        return BPFMode.valueOf(cmd.args.get(Param.mode));
    }
}
