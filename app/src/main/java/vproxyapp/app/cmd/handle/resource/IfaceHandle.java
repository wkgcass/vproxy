package vproxyapp.app.cmd.handle.resource;

import vproxyapp.app.Application;
import vproxyapp.app.cmd.Resource;
import vproxyapp.app.cmd.ResourceType;
import vswitch.Switch;
import vswitch.iface.Iface;

import java.util.List;

public class IfaceHandle {
    private IfaceHandle() {
    }

    public static void checkIface(Resource parent) throws Exception {
        if (parent == null)
            throw new Exception("cannot find " + ResourceType.iface.fullname + " on top level");
        if (parent.type != ResourceType.sw)
            throw new Exception(parent.type.fullname + " does not contain " + ResourceType.iface.fullname);
        SwitchHandle.checkSwitch(parent);
    }

    public static int count(Resource parent) throws Exception {
        return list(parent).size();
    }

    public static List<Iface> list(Resource parent) throws Exception {
        Switch sw = Application.get().switchHolder.get(parent.alias);
        return sw.getIfaces();
    }
}
