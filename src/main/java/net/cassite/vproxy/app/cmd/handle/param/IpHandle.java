package net.cassite.vproxy.app.cmd.handle.param;

import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.app.cmd.Param;
import net.cassite.vproxy.dns.Resolver;
import net.cassite.vproxy.util.BlockCallback;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class IpHandle {
    private IpHandle() {
    }

    public static InetAddress get(Command cmd) throws Exception {
        BlockCallback<InetAddress, UnknownHostException> cb = new BlockCallback<>();
        Resolver.getDefault().resolve(cmd.args.get(Param.ip), cb);
        return cb.block();
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
