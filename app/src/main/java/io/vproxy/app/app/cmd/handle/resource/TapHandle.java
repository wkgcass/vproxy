package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.base.util.exception.XException;
import io.vproxy.vswitch.Switch;

public class TapHandle {
    private TapHandle() {
    }

    public static void add(Command cmd) throws Exception {
        String dev = cmd.resource.alias;
        if (dev.contains("%")) {
            throw new XException("wildcard % in tap dev is forbidden");
        }
        int vni = Integer.parseInt(cmd.args.get(Param.vni));
        String postScript = cmd.args.get(Param.postscript);
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.addTap(dev, vni, postScript);
    }
}
