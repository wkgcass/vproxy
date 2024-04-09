package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.app.app.cmd.handle.param.IpParamHandle;
import io.vproxy.base.util.exception.XException;
import io.vproxy.vfd.IPv4;
import io.vproxy.vswitch.Switch;

public class FubukiEtherIPHandle {
    private FubukiEtherIPHandle() {
    }

    public static void add(Command cmd) throws Exception {
        var name = cmd.resource.alias;

        var vni = Integer.parseInt(cmd.args.get(Param.vni));
        var ip = IpParamHandle.get(cmd);
        if (!(ip instanceof IPv4)) {
            throw new XException(ip + " is not valid ipv4");
        }

        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.addFubukiEtherIP(name, vni, (IPv4) ip);
    }
}
