package io.vproxy.app.app.cmd.handle.param;

import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;

import java.util.Arrays;

public class OffloadHandle {
    private OffloadHandle() {
    }

    public static final String PACKET_SWITCHING = "pktsw";
    public static final String CHECKSUM = "csum";

    public static boolean check(Command cmd, String... available) {
        if (!cmd.args.containsKey(Param.offload)) {
            return true;
        }
        var lsAvailable = Arrays.asList(available);
        var offload = cmd.args.get(Param.offload);
        var split = offload.split(",");
        for (var s : split) {
            s = s.trim();
            if (!lsAvailable.contains(s)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isPacketSwitchingOffloaded(Command cmd) {
        return isOffloaded(cmd, PACKET_SWITCHING);
    }

    public static boolean isChecksumOffloaded(Command cmd) {
        return isOffloaded(cmd, CHECKSUM);
    }

    private static boolean isOffloaded(Command cmd, String match) {
        var offload = cmd.args.get(Param.offload);
        if (offload == null) {
            return false;
        }
        var split = offload.split(",");
        for (var s : split) {
            s = s.trim();
            if (s.equals(match)) {
                return true;
            }
        }
        return false;
    }
}
