package vproxy.app.app.cmd.handle.resource;

import vjson.util.ObjectBuilder;
import vproxy.app.app.Application;
import vproxy.app.app.ServerGroupHolder;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.Resource;
import vproxy.app.app.cmd.ResourceType;
import vproxy.app.app.cmd.handle.param.AnnotationsHandle;
import vproxy.app.app.cmd.handle.param.HealthCheckHandle;
import vproxy.app.app.cmd.handle.param.MethHandle;
import vproxy.app.app.cmd.handle.param.WeightHandle;
import vproxy.base.component.check.HealthCheckConfig;
import vproxy.base.component.elgroup.EventLoopGroup;
import vproxy.base.component.svrgroup.ServerGroup;
import vproxy.base.util.Annotations;
import vproxy.base.util.exception.NotFoundException;
import vproxy.base.util.exception.XException;
import vproxy.component.svrgroup.Upstream;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ServerGroupHandle {
    private ServerGroupHandle() {
    }

    public static ServerGroup get(Resource resource) throws Exception {
        if (resource.parentResource == null)
            return Application.get().serverGroupHolder.get(resource.alias);
        List<Upstream.ServerGroupHandle> ls = UpstreamHandle.get(resource.parentResource).getServerGroupHandles();
        for (Upstream.ServerGroupHandle s : ls) {
            if (s.alias.equals(resource.alias))
                return s.group;
        }
        throw new NotFoundException("server-group in upstream " + resource.parentResource.alias, resource.alias);
    }

    public static Upstream.ServerGroupHandle getHandle(Resource resource) throws Exception {
        List<Upstream.ServerGroupHandle> ls = UpstreamHandle.get(resource.parentResource).getServerGroupHandles();
        for (Upstream.ServerGroupHandle s : ls) {
            if (s.alias.equals(resource.alias))
                return s;
        }
        throw new NotFoundException("server-group in upstream " + resource.parentResource.alias, resource.alias);
    }

    public static List<String> names() throws Exception {
        return Application.get().serverGroupHolder.names();
    }

    public static List<String> names(Resource targetResource) throws Exception {
        return UpstreamHandle.get(targetResource).getServerGroupHandles()
            .stream().map(g -> g.alias).collect(Collectors.toList());
    }

    public static List<ServerGroupRef> details() throws Exception {
        ServerGroupHolder holder = Application.get().serverGroupHolder;
        List<String> names = holder.names();
        List<ServerGroupRef> list = new LinkedList<>();
        for (String name : names) {
            list.add(new ServerGroupRef(name, holder.get(name)));
        }
        return list;
    }

    public static List<ServerGroupRef> details(Resource targetResource) throws Exception {
        return UpstreamHandle.get(targetResource).getServerGroupHandles()
            .stream().map(h -> new ServerGroupRef(h.alias, h)).collect(Collectors.toList());
    }

    public static void add(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.elg)) {
            cmd.args.put(Param.elg, Application.DEFAULT_CONTROL_EVENT_LOOP_GROUP_NAME);
        }

        String alias = cmd.resource.alias;
        String eventLoopGroupName = cmd.args.get(Param.elg);
        EventLoopGroup elg = EventLoopGroupHandle.get(eventLoopGroupName);
        HealthCheckConfig c = HealthCheckHandle.getHealthCheckConfig(cmd);
        Annotations anno = null;
        if (cmd.args.containsKey(Param.anno)) {
            anno = AnnotationsHandle.get(cmd);
        }
        Application.get().serverGroupHolder.add(alias, elg, c, MethHandle.get(cmd, "wrr"), anno);
    }

    public static void attach(Command cmd) throws Exception {
        int weight = WeightHandle.get(cmd);
        var h = Application.get().upstreamHolder.get(cmd.prepositionResource.alias)
            .add(Application.get().serverGroupHolder.get(cmd.resource.alias), weight);
        if (cmd.args.containsKey(Param.anno)) {
            h.setAnnotations(AnnotationsHandle.get(cmd));
        }
    }

    public static void preRemoveCheck(Command cmd) throws Exception {
        // remove top level server group
        ServerGroup serverGroup = Application.get().serverGroupHolder.get(cmd.resource.alias);

        // check upstream
        for (String upstreamName : Application.get().upstreamHolder.names()) {
            Upstream groups = Application.get().upstreamHolder.get(upstreamName);
            if (groups.getServerGroupHandles().stream().anyMatch(h -> h.group.equals(serverGroup))) {
                throw new XException(ResourceType.sg.fullname + " " + serverGroup.alias + " is used by " + ResourceType.ups.fullname + " " + groups.alias);
            }
        }
    }

    public static void remove(Command cmd) throws Exception {
        // remove top level server group
        Application.get().serverGroupHolder.removeAndClear(cmd.resource.alias);
    }

    public static void detach(Command cmd) throws Exception {
        // detach from upstream
        ServerGroup g = Application.get().serverGroupHolder.get(cmd.resource.alias);
        UpstreamHandle.get(cmd.prepositionResource).remove(g);
    }

    public static void update(Command cmd) throws Exception {
        ServerGroup g = Application.get().serverGroupHolder.get(cmd.resource.alias);
        if (cmd.args.containsKey(Param.timeout)) {
            g.setHealthCheckConfig(HealthCheckHandle.getHealthCheckConfig(cmd));
        }
        if (cmd.args.containsKey(Param.meth)) {
            g.setMethod(MethHandle.get(cmd, ""));
        }
        if (cmd.args.containsKey(Param.anno)) {
            g.setAnnotations(AnnotationsHandle.get(cmd));
        }
    }

    public static void updateInUpstream(Command cmd) throws Exception {
        Upstream.ServerGroupHandle h = getHandle(cmd.resource);
        if (cmd.args.containsKey(Param.weight)) {
            h.setWeight(WeightHandle.get(cmd));
        }
        if (cmd.args.containsKey(Param.anno)) {
            h.setAnnotations(AnnotationsHandle.get(cmd));
        }
    }

    public static class ServerGroupRef {
        private final String alias;
        private final ServerGroup g;
        private final Upstream.ServerGroupHandle h;

        public ServerGroupRef(String alias, ServerGroup g) {
            this.alias = alias;
            this.g = g;
            this.h = null;
        }

        public ServerGroupRef(String alias, Upstream.ServerGroupHandle h) {
            this.alias = alias;
            this.h = h;
            this.g = h.group;
        }

        @Override
        public String toString() {
            HealthCheckConfig c = g.getHealthCheckConfig();
            return alias + " -> timeout " + c.timeout + " period " + c.period +
                " up " + c.up + " down " + c.down + " protocol " + c.checkProtocol.name() +
                " method " + g.getMethod() +
                " event-loop-group " + g.eventLoopGroup.alias +
                " annotations " + formatAnno() +
                (h == null ? "" : " weight " + h.getWeight());
        }

        private String formatAnno() {
            Annotations annos;
            if (h == null) {
                annos = g.getAnnotations();
            } else {
                annos = h.getAnnotations();
            }

            return annos.toString();
        }
    }
}
