package io.vproxy.app.app.cmd.handle.param;

import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.base.util.exception.XException;
import io.vproxy.vswitch.dispatcher.BPFMapKeySelector;
import io.vproxy.vswitch.dispatcher.BPFMapKeySelectors;

public class BPFMapKeySelectorHandle {
    private BPFMapKeySelectorHandle() {
    }

    public static void check(Command cmd) throws XException {
        try {
            BPFMapKeySelectors.valueOf(cmd.args.get(Param.bpfmapkeyselector));
        } catch (IllegalArgumentException e) {
            throw new XException("unknown " + Param.bpfmapkeyselector.fullname + ": " + cmd.args.get(Param.bpfmapkeyselector));
        }
    }

    public static BPFMapKeySelector get(Command cmd) {
        return BPFMapKeySelectors.valueOf(cmd.args.get(Param.bpfmapkeyselector)).keySelector.get();
    }
}
