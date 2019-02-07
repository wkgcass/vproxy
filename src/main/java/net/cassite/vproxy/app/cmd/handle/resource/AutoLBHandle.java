package net.cassite.vproxy.app.cmd.handle.resource;

import net.cassite.vproxy.app.Application;
import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.app.cmd.Param;
import net.cassite.vproxy.app.cmd.Resource;
import net.cassite.vproxy.app.cmd.ResourceType;
import net.cassite.vproxy.app.cmd.handle.param.PortHandle;
import net.cassite.vproxy.app.cmd.handle.param.ServiceHandle;
import net.cassite.vproxy.app.cmd.handle.param.ZoneHandle;
import net.cassite.vproxy.app.mesh.AutoLBHolder;
import net.cassite.vproxy.component.auto.AutoLB;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.component.exception.XException;

import java.util.LinkedList;
import java.util.List;

public class AutoLBHandle {
    private AutoLBHandle() {
    }

    public static void check(Resource parent) throws XException {
        if (parent != null)
            throw new XException(ResourceType.autolb.fullname + " is on top level");
    }

    public static void checkCreate(Command cmd) throws XException {
        if (!cmd.args.containsKey(Param.service))
            throw new XException("missing argument " + Param.service.fullname);
        if (!cmd.args.containsKey(Param.zone))
            throw new XException("missing argument " + Param.zone.fullname);
        if (!cmd.args.containsKey(Param.port))
            throw new XException("missing argument " + Param.port.fullname);

        PortHandle.check(cmd);
    }

    public static List<String> names() {
        return Application.get().autoLBHolder.names();
    }

    public static List<AutoLB> detail() {
        AutoLBHolder holder = Application.get().autoLBHolder;
        List<String> names = holder.names();
        List<AutoLB> autoLBs = new LinkedList<>();
        for (String name : names) {
            try {
                AutoLB a = holder.get(name);
                autoLBs.add(a);
            } catch (NotFoundException ignore) {
            }
        }
        return autoLBs;
    }

    public static void remove(Command cmd) throws NotFoundException {
        String alias = cmd.resource.alias;
        Application.get().autoLBHolder.remove(alias);
    }

    public static void add(Command cmd) throws Exception {
        String alias = cmd.resource.alias;
        String service = ServiceHandle.get(cmd);
        String zone = ZoneHandle.get(cmd);
        int port = PortHandle.get(cmd);

        Application.get().autoLBHolder.add(alias, service, zone, port);
    }
}
