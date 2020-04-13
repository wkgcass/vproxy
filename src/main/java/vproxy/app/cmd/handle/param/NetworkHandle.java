package vproxy.app.cmd.handle.param;

import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;
import vproxy.component.exception.XException;
import vproxy.util.Tuple;
import vproxy.util.Utils;

public class NetworkHandle {
    private NetworkHandle() {
    }

    public static void check(Command cmd) throws Exception {
        try {
            get(cmd);
        } catch (Exception e) {
            throw new XException("invalid format for " + Param.net.fullname);
        }
    }

    public static Tuple<byte[], byte[]> get(Command cmd) {
        String net = cmd.args.get(Param.net);
        String[] arr = net.split("/");
        if (arr.length > 2)
            throw new IllegalArgumentException();
        byte[] addr = Utils.blockParseAddress(arr[0]);
        byte[] mask = Utils.parseMask(Integer.parseInt(arr[1]));
        if (!Utils.validNetwork(addr, mask))
            throw new IllegalArgumentException();
        return new Tuple<>(addr, mask);
    }
}
