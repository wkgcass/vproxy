package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.*;
import io.vproxy.app.app.cmd.handle.param.AnnotationsHandle;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.vswitch.Switch;
import io.vproxy.vswitch.iface.Iface;

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
        IfaceParamsHandleHelper.update(cmd, target.getParams());
        if (cmd.args.containsKey(Param.anno)) {
            target.setAnnotations(AnnotationsHandle.get(cmd));
        }
        if (cmd.flags.contains(Flag.enable)) {
            target.setDisabled(false);
        }
        if (cmd.flags.contains(Flag.disable)) {
            target.setDisabled(true);
        }
    }

    public static void remove(Command cmd) throws Exception {
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.delIface(cmd.resource.alias);
    }
}
