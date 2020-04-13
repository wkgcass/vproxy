package vproxy.app.cmd.handle.param;

import vproxy.app.cmd.Command;
import vproxy.app.cmd.Flag;
import vproxy.app.cmd.Param;
import vproxy.component.exception.XException;
import vproxy.dns.Resolver;
import vproxy.util.BlockCallback;

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
        Resolver.getDefault().resolve(addrIp, ipv4, ipv6, cb);
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
            throw new XException("invalid format for " + Param.addr.fullname);
        }
    }
}
