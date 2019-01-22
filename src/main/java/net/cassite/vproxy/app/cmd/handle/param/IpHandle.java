package net.cassite.vproxy.app.cmd.handle.param;

import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.app.cmd.Param;

import java.net.InetAddress;

public class IpHandle {
    private IpHandle() {
    }

    public static InetAddress get(Command cmd) throws Exception {
        return InetAddress.getByName(cmd.args.get(Param.ip));
    }

    public static void check(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.ip))
            throw new Exception("missing argument " + Param.ip.fullname);

        try {
            get(cmd);
        } catch (Exception e) {
            throw new Exception("invalid format for " + Param.ip.fullname);
        }
    }
}
