package vproxyapp.app.cmd.handle.resource;

import vproxyapp.app.Application;
import vproxyapp.app.cmd.Command;
import vproxyapp.app.cmd.Param;
import vproxyapp.app.cmd.Resource;
import vproxyapp.app.cmd.ResourceType;
import vproxybase.util.exception.XException;
import vproxybase.util.Utils;
import vswitch.Switch;

public class TapHandle {
    private TapHandle() {
    }

    public static void checkTap(Resource parent) throws Exception {
        if (parent == null)
            throw new Exception("cannot find " + ResourceType.tap.fullname + " on top level");
        if (parent.type != ResourceType.sw)
            throw new Exception(parent.type.fullname + " does not contain " + ResourceType.tap.fullname);
        SwitchHandle.checkSwitch(parent);
    }

    public static void checkCreateTap(Command cmd) throws Exception {
        String devPattern = cmd.resource.alias;
        if (devPattern.length() > 10) {
            throw new XException("tap dev name pattern too long: should <= 10");
        }
        String vni = cmd.args.get(Param.vni);
        if (vni == null) {
            throw new Exception("missing " + Param.vni.fullname);
        }
        if (!Utils.isInteger(vni)) {
            throw new Exception("invalid " + Param.vni.fullname + ", not an integer");
        }
    }

    public static String add(Command cmd) throws Exception {
        String devPattern = cmd.resource.alias;
        int vni = Integer.parseInt(cmd.args.get(Param.vni));
        String postScript = cmd.args.get(Param.postscript);
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        return sw.addTap(devPattern, vni, postScript);
    }

    public static void forceRemove(Command cmd) throws Exception {
        String devPattern = cmd.resource.alias;
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.delTap(devPattern);
    }
}
