package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.Resource;
import vproxy.app.app.cmd.handle.param.AnnotationsHandle;
import vproxy.base.component.elgroup.EventLoopGroup;
import vproxy.base.component.elgroup.EventLoopWrapper;
import vproxy.base.util.Annotations;
import vproxy.base.util.exception.XException;

import java.util.ArrayList;
import java.util.List;

public class EventLoopHandle {
    private EventLoopHandle() {
    }

    public static EventLoopWrapper get(Resource resource) throws Exception {
        String groupName = resource.parentResource.alias;
        return Application.get().eventLoopGroupHolder.get(groupName).get(resource.alias);
    }

    public static List<String> names(Resource targetResource) throws Exception {
        EventLoopGroup g = EventLoopGroupHandle.get(targetResource);
        return g.names();
    }

    public static List<EventLoopWrapper> detail(Resource targetResource) throws Exception {
        var g = EventLoopGroupHandle.get(targetResource);
        var names = g.names();
        var ls = new ArrayList<EventLoopWrapper>(names.size());
        for (String name : names) {
            ls.add(g.get(name));
        }
        return ls;
    }

    public static void add(Command cmd) throws Exception {
        EventLoopGroup g = EventLoopGroupHandle.get(cmd.prepositionResource);
        if (Application.isDefaultEventLoopGroupName(g.alias))
            throw new XException("cannot modify the default event loop group " + g.alias);
        Annotations anno;
        if (cmd.args.containsKey(Param.anno)) {
            anno = AnnotationsHandle.get(cmd);
        } else {
            anno = new Annotations();
        }
        g.add(cmd.resource.alias, anno);
    }

    public static void remove(Command cmd) throws Exception {
        EventLoopGroup g = EventLoopGroupHandle.get(cmd.prepositionResource);
        if (Application.isDefaultEventLoopGroupName(g.alias))
            throw new XException("cannot modify the default event loop group " + g.alias);
        g.remove(cmd.resource.alias);
    }
}
