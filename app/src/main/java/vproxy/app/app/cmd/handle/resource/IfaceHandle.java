package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Resource;
import vproxy.app.app.cmd.ResourceType;
import vproxy.vswitch.Switch;
import vproxy.vswitch.iface.Iface;

import java.util.List;

public class IfaceHandle {
    private IfaceHandle() {
    }

    public static void checkIfaceParent(Resource parent) throws Exception {
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
