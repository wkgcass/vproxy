package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.app.app.cmd.handle.param.AddrHandle;
import io.vproxy.app.app.cmd.handle.param.IpParamHandle;
import io.vproxy.app.app.cmd.handle.param.MacHandle;
import io.vproxy.vfd.IPMask;
import io.vproxy.vswitch.Switch;

public class FubukiHandle {
    private FubukiHandle() {
    }

    public static void add(Command cmd) throws Exception {
        var node = cmd.resource.alias;
        var pass = cmd.args.get(Param.pass);
        var vni = Integer.parseInt(cmd.args.get(Param.vni));
        var mac = MacHandle.get(cmd);
        var addr = AddrHandle.get(cmd);
        IPMask localAddr = null;
        if (cmd.args.containsKey(Param.ip)) {
            localAddr = IpParamHandle.getIPMask(cmd);
        }
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.addFubuki(node, pass, vni, mac, addr, localAddr);
    }
}
