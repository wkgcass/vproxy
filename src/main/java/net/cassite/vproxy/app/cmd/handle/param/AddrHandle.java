package net.cassite.vproxy.app.cmd.handle.param;

import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.app.cmd.Param;

import java.net.InetSocketAddress;

public class AddrHandle {
    private AddrHandle() {
    }

    public static InetSocketAddress get(Command cmd) {
        String addrStr = cmd.args.get(Param.addr);
        return get(addrStr);
    }

    public static InetSocketAddress get(String addrStr) {
        int idx = addrStr.lastIndexOf(":");
        String addrIp = addrStr.substring(0, idx);
        int addrPort = Integer.parseInt(addrStr.substring(idx + 1));
        return new InetSocketAddress(addrIp, addrPort);
    }

    public static void check(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.addr))
            throw new Exception("missing argument " + Param.addr.fullname);

        try {
            get(cmd);
        } catch (Exception e) {
            throw new Exception("invalid format for " + Param.addr.fullname);
        }
    }
}
