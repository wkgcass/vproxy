package vproxy.app.app.cmd.handle.param;

import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.base.util.exception.XException;
import vproxy.vswitch.dispatcher.BPFMapKeySelector;
import vproxy.vswitch.dispatcher.BPFMapKeySelectors;

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
