package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.handle.param.FloodHandle;
import vproxy.app.app.cmd.handle.param.MTUHandle;
import vproxy.app.app.cmd.handle.param.MacHandle;
import vproxy.vfd.MacAddress;
import vproxy.vswitch.Switch;

public class TunHandle {
    private TunHandle() {
    }

    public static String add(Command cmd) throws Exception {
        String devPattern = cmd.resource.alias;
        int vni = Integer.parseInt(cmd.args.get(Param.vni));
        MacAddress mac = MacHandle.get(cmd);
        String postScript = cmd.args.get(Param.postscript);
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        Integer mtu = null;
        if (cmd.args.containsKey(Param.mtu)) {
            mtu = MTUHandle.get(cmd);
        }
        Boolean flood = null;
        if (cmd.args.containsKey(Param.flood)) {
            flood = FloodHandle.get(cmd);
        }
        var tun = sw.addTun(devPattern, vni, mac, postScript, mtu, flood);
        return tun.getTun().getTap().dev;
    }

    public static void remove(Command cmd) throws Exception {
        String devPattern = cmd.resource.alias;
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.delTun(devPattern);
    }
}
