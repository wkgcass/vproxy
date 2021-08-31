package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.handle.param.MacHandle;
import vproxy.base.util.exception.XException;
import vproxy.vfd.MacAddress;
import vproxy.vswitch.Switch;

public class TunHandle {
    private TunHandle() {
    }

    public static void add(Command cmd) throws Exception {
        String dev = cmd.resource.alias;
        if (dev.contains("%")) {
            throw new XException("wildcard % in tun dev is forbidden");
        }
        int vni = Integer.parseInt(cmd.args.get(Param.vni));
        MacAddress mac = MacHandle.get(cmd);
        String postScript = cmd.args.get(Param.postscript);
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.addTun(dev, vni, mac, postScript);
    }
}
