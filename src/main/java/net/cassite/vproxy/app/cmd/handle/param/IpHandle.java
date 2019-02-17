package net.cassite.vproxy.app.cmd.handle.param;

import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.app.cmd.Flag;
import net.cassite.vproxy.app.cmd.Param;
import net.cassite.vproxy.dns.Resolver;
import net.cassite.vproxy.util.BlockCallback;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class IpHandle {
    private IpHandle() {
    }

    public static InetAddress get(Command cmd) throws Exception {
        boolean ipv4 = !cmd.flags.contains(Flag.noipv4);
        boolean ipv6 = !cmd.flags.contains(Flag.noipv6);
        String ip = cmd.args.get(Param.ip);
        return get(ip, ipv4, ipv6);
    }

    public static InetAddress get(String addrIp, boolean ipv4, boolean ipv6) throws Exception {
        BlockCallback<InetAddress, UnknownHostException> cb = new BlockCallback<>();
        Resolver.getDefault().resolve(addrIp, ipv4, ipv6, cb);
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
