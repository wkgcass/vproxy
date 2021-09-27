package vproxy.app.app.cmd.handle.param;

import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Flag;
import vproxy.app.app.cmd.Param;
import vproxy.base.dns.Resolver;
import vproxy.base.util.callback.BlockCallback;
import vproxy.base.util.exception.XException;
import vproxy.vfd.IP;
import vproxy.vfd.IPPort;
import vproxy.vfd.UDSPath;

import java.net.UnknownHostException;

public class AddrHandle {
    private AddrHandle() {
    }

    public static IPPort get(Command cmd) throws Exception {
        boolean ipv4 = !cmd.flags.contains(Flag.noipv4);
        boolean ipv6 = !cmd.flags.contains(Flag.noipv6);
        String addrStr = cmd.args.get(Param.addr);
        if (addrStr.startsWith("sock:")) {
            var path = addrStr.substring("sock:".length()).trim();
            if (path.isBlank()) {
                throw new XException("invalid unix domain socket: path not specified");
            }
            return new UDSPath(path);
        }
        return get(addrStr, ipv4, ipv6);
    }

    public static IPPort get(String addrStr, boolean ipv4, boolean ipv6) throws Exception {
        int idx = addrStr.lastIndexOf(":");
        String addrIp = addrStr.substring(0, idx);
        int addrPort = Integer.parseInt(addrStr.substring(idx + 1));

        BlockCallback<IP, UnknownHostException> cb = new BlockCallback<>();
        Resolver.getDefault().resolve(addrIp, ipv4, ipv6, cb);
        IP addr = cb.block();
        return new IPPort(addr, addrPort);
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
