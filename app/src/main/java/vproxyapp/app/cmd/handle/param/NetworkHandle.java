package vproxyapp.app.cmd.handle.param;

import vfd.IP;
import vproxyapp.app.cmd.Command;
import vproxyapp.app.cmd.Param;
import vproxybase.util.Network;
import vproxybase.util.exception.XException;

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

    public static Network get(String net) {
        String[] arr = net.split("/");
        if (arr.length > 2)
            throw new IllegalArgumentException();
        byte[] addr = IP.blockParseAddress(arr[0]);
        byte[] mask = Network.parseMask(Integer.parseInt(arr[1]));
        if (!Network.validNetwork(addr, mask))
            throw new IllegalArgumentException();
        return new Network(addr, mask);
    }

    public static Network get(Command cmd) {
        String net = cmd.args.get(Param.net);
        return get(net);
    }
}
