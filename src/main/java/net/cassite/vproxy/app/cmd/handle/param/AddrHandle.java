package net.cassite.vproxy.app.cmd.handle.param;

import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.app.cmd.Flag;
import net.cassite.vproxy.app.cmd.Param;
import net.cassite.vproxy.dns.Resolver;
import net.cassite.vproxy.util.BlockCallback;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class AddrHandle {
    private AddrHandle() {
    }

    public static InetSocketAddress get(Command cmd) throws Exception {
        boolean ipv4 = !cmd.flags.contains(Flag.noipv4);
        boolean ipv6 = !cmd.flags.contains(Flag.noipv6);
        String addrStr = cmd.args.get(Param.addr);
        return get(addrStr, ipv4, ipv6);
    }

    public static InetSocketAddress get(String addrStr, boolean ipv4, boolean ipv6) throws Exception {
        int idx = addrStr.lastIndexOf(":");
        String addrIp = addrStr.substring(0, idx);
        int addrPort = Integer.parseInt(addrStr.substring(idx + 1));

        BlockCallback<InetAddress, UnknownHostException> cb = new BlockCallback<>();
        if (ipv4 && ipv6) {
            Resolver.getDefault().resolve(addrIp, cb);
        } else if (ipv4) {
            Resolver.getDefault().resolveV4(addrIp, cb);
        } else {
            assert ipv6;
            Resolver.getDefault().resolveV6(addrIp, cb);
        }
        InetAddress addr = cb.block();
        return new InetSocketAddress(addr, addrPort);
    }

    public static void check(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.addr))
            throw new Exception("missing argument " + Param.addr.fullname);
        if (cmd.flags.contains(Flag.noipv4) && cmd.flags.contains(Flag.noipv6))
            throw new Exception("both " + Flag.noipv4.fullname + " and " + Flag.noipv6.fullname + " set");

        try {
            get(cmd);
        } catch (Exception e) {
            throw new Exception("invalid format for " + Param.addr.fullname);
        }
    }
}
