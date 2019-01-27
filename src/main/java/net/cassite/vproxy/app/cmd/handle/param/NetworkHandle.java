package net.cassite.vproxy.app.cmd.handle.param;

import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.app.cmd.Param;
import net.cassite.vproxy.util.Tuple;
import net.cassite.vproxy.util.Utils;

public class NetworkHandle {
    private NetworkHandle() {
    }

    public static void check(Command cmd) throws Exception {
        try {
            get(cmd);
        } catch (Exception e) {
            throw new Exception("invalid format for " + Param.net.fullname);
        }
    }

    public static Tuple<byte[], byte[]> get(Command cmd) {
        String net = cmd.args.get(Param.net);
        String[] arr = net.split("/");
        if (arr.length > 2)
            throw new IllegalArgumentException();
        byte[] addr = Utils.parseAddress(arr[0]);
        byte[] mask = Utils.parseMask(Integer.parseInt(arr[1]));
        if (!Utils.validNetwork(addr, mask))
            throw new IllegalArgumentException();
        return new Tuple<>(addr, mask);
    }
}
