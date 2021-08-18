package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.Resource;
import vproxy.app.app.cmd.ResourceType;
import vproxy.app.app.cmd.handle.param.AnnotationsHandle;
import vproxy.app.app.cmd.handle.param.FloodHandle;
import vproxy.app.app.cmd.handle.param.MTUHandle;
import vproxy.base.util.exception.NotFoundException;
import vproxy.vswitch.Switch;
import vproxy.vswitch.iface.Iface;

import java.util.List;

public class IfaceHandle {
    private IfaceHandle() {
    }

    public static Iface get(Resource self) throws Exception {
        return get(self.parentResource, self.alias);
    }

    public static Iface get(Resource parent, String name) throws Exception {
        List<Iface> ifaces = list(parent);

        Iface target = null;
        for (Iface iface : ifaces) {
            if (iface.name().equals(name)) {
                target = iface;
                break;
            }
        }
        if (target == null) {
            throw new NotFoundException(ResourceType.iface.fullname, name);
        }
        return target;
    }

    public static int count(Resource parent) throws Exception {
        return list(parent).size();
    }

    public static List<Iface> list(Resource parent) throws Exception {
        Switch sw = Application.get().switchHolder.get(parent.alias);
        return sw.getIfaces();
    }

    public static void update(Command cmd) throws Exception {
        Iface target = get(cmd.resource);
        if (cmd.args.containsKey(Param.mtu)) {
            target.setBaseMTU(MTUHandle.get(cmd));
        }
        if (cmd.args.containsKey(Param.flood)) {
            target.setFloodAllowed(FloodHandle.get(cmd));
        }
        if (cmd.args.containsKey(Param.anno)) {
            target.setAnnotations(AnnotationsHandle.get(cmd));
        }
    }
}
