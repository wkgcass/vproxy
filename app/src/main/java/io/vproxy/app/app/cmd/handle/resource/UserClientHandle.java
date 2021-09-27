package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.app.app.cmd.handle.param.AddrHandle;
import io.vproxy.vfd.IPPort;
import io.vproxy.vswitch.Switch;

public class UserClientHandle {
    private UserClientHandle() {
    }

    public static void add(Command cmd) throws Exception {
        String user = cmd.resource.alias;
        String pass = cmd.args.get(Param.pass);
        int vni = Integer.parseInt(cmd.args.get(Param.vni));
        IPPort addr = AddrHandle.get(cmd);
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.addUserClient(user, pass, vni, addr);
    }
}
