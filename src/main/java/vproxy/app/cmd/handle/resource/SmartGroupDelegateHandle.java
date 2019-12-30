package vproxy.app.cmd.handle.resource;

import vproxy.app.Application;
import vproxy.app.Config;
import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;
import vproxy.app.cmd.Resource;
import vproxy.app.cmd.ResourceType;
import vproxy.app.cmd.handle.param.ServiceHandle;
import vproxy.app.cmd.handle.param.ZoneHandle;
import vproxy.app.mesh.SmartGroupDelegateHolder;
import vproxy.component.auto.SmartGroupDelegate;
import vproxy.component.exception.NotFoundException;
import vproxy.component.exception.XException;
import vproxy.component.svrgroup.ServerGroup;

import java.util.LinkedList;
import java.util.List;

public class SmartGroupDelegateHandle {
    private SmartGroupDelegateHandle() {
    }

    public static void check(Resource parent) throws XException {
        if (parent != null)
            throw new XException(ResourceType.sgd.fullname + " is on top level");
    }

    public static void checkCreate(Command cmd) throws XException {
        if (!cmd.args.containsKey(Param.service))
            throw new XException("missing argument " + Param.service.fullname);
        if (!cmd.args.containsKey(Param.zone))
            throw new XException("missing argument " + Param.zone.fullname);
        if (!cmd.args.containsKey(Param.sg))
            throw new XException("missing argument " + Param.sg.fullname);
    }

    public static List<String> names() {
        return Application.get().smartGroupDelegateHolder.names();
    }

    public static List<SmartGroupDelegate> detail() {
        SmartGroupDelegateHolder holder = Application.get().smartGroupDelegateHolder;
        List<String> names = holder.names();
        List<SmartGroupDelegate> smartGroupDelegates = new LinkedList<>();
        for (String name : names) {
            try {
                SmartGroupDelegate s = holder.get(name);
                smartGroupDelegates.add(s);
            } catch (NotFoundException ignore) {
            }
        }
        return smartGroupDelegates;
    }

    public static void remove(Command cmd) throws NotFoundException {
        String alias = cmd.resource.alias;
        Application.get().smartGroupDelegateHolder.remove(alias);
    }

    public static void add(Command cmd) throws Exception {
        String alias = cmd.resource.alias;
        String service = ServiceHandle.get(cmd);
        String zone = ZoneHandle.get(cmd);
        ServerGroup sg = Application.get().serverGroupHolder.get(cmd.args.get(Param.sg));

        Application.get().smartGroupDelegateHolder.add(alias, service, zone, sg);
    }
}
