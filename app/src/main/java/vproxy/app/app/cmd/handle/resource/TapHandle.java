package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.handle.param.AnnotationsHandle;
import vproxy.app.app.cmd.handle.param.FloodHandle;
import vproxy.app.app.cmd.handle.param.MTUHandle;
import vproxy.base.util.Annotations;
import vproxy.vswitch.Switch;

public class TapHandle {
    private TapHandle() {
    }

    public static String add(Command cmd) throws Exception {
        String devPattern = cmd.resource.alias;
        int vni = Integer.parseInt(cmd.args.get(Param.vni));
        String postScript = cmd.args.get(Param.postscript);
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        Annotations anno = null;
        if (cmd.args.containsKey(Param.anno)) {
            anno = AnnotationsHandle.get(cmd);
        }
        Integer mtu = null;
        if (cmd.args.containsKey(Param.mtu)) {
            mtu = MTUHandle.get(cmd);
        }
        Boolean flood = null;
        if (cmd.args.containsKey(Param.flood)) {
            flood = FloodHandle.get(cmd);
        }
        var tap = sw.addTap(devPattern, vni, postScript, anno, mtu, flood);
        return tap.getTap().getTap().dev;
    }

    public static void remove(Command cmd) throws Exception {
        String devPattern = cmd.resource.alias;
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.delTap(devPattern);
    }
}
