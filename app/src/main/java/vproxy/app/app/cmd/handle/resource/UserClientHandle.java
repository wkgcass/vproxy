package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.handle.param.AddrHandle;
import vproxy.vfd.IPPort;
import vproxy.vswitch.Switch;

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

    public static void forceRemove(Command cmd) throws Exception {
        String user = cmd.resource.alias;
        IPPort addr = AddrHandle.get(cmd);
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.delUserClient(user, addr);
    }
}
