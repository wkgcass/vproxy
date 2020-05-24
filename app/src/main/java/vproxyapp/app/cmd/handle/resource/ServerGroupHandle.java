package vproxyapp.app.cmd.handle.resource;

import vjson.util.ObjectBuilder;
import vproxy.component.svrgroup.Upstream;
import vproxyapp.app.Application;
import vproxyapp.app.ServerGroupHolder;
import vproxyapp.app.cmd.Command;
import vproxyapp.app.cmd.Param;
import vproxyapp.app.cmd.Resource;
import vproxyapp.app.cmd.ResourceType;
import vproxyapp.app.cmd.handle.param.AnnotationsHandle;
import vproxyapp.app.cmd.handle.param.HealthCheckHandle;
import vproxyapp.app.cmd.handle.param.MethHandle;
import vproxyapp.app.cmd.handle.param.WeightHandle;
import vproxybase.component.check.HealthCheckConfig;
import vproxybase.component.elgroup.EventLoopGroup;
import vproxybase.component.svrgroup.ServerGroup;
import vproxybase.util.exception.NotFoundException;
import vproxybase.util.exception.XException;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public static void checkAttachServerGroup(Command cmd) throws Exception {
        if (cmd.args.containsKey(Param.w))
            WeightHandle.check(cmd);
        if (cmd.args.containsKey(Param.anno))
            AnnotationsHandle.check(cmd);
    }

    public static void checkCreateServerGroup(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.timeout))
            throw new Exception("missing argument " + Param.timeout.fullname);
        if (!cmd.args.containsKey(Param.period))
            throw new Exception("missing argument " + Param.period.fullname);
        if (!cmd.args.containsKey(Param.up))
            throw new Exception("missing argument " + Param.up.fullname);
        if (!cmd.args.containsKey(Param.down))
            throw new Exception("missing argument " + Param.down.fullname);

        try {
            HealthCheckHandle.getHealthCheckConfig(cmd);
        } catch (Exception e) {
            throw new XException("invalid health check config");
        }
        if (cmd.args.containsKey(Param.meth)) {
            try {
                MethHandle.get(cmd);
            } catch (Exception e) {
                throw new XException("invalid method");
            }
        } else {
            cmd.args.put(Param.meth, "wrr");
        }

        if (cmd.args.containsKey(Param.anno)) {
            AnnotationsHandle.check(cmd);
        }
    }

    public static void checkUpdateServerGroup(Command cmd) throws Exception {
        if (cmd.resource.parentResource == null) {
            // can only update the server group self info on top level
            // i'm not saying that you cannot modify the one in upstream
            // but you don't have to go into upstream to modify,
            // the one on top level is the same one in any upstream
            if (cmd.args.containsKey(Param.timeout)
                || cmd.args.containsKey(Param.period)
                || cmd.args.containsKey(Param.up)
                || cmd.args.containsKey(Param.down)) {
                try {
                    HealthCheckHandle.getHealthCheckConfig(cmd);
                } catch (Exception e) {
                    throw new XException("missing health check argument or is invalid");
                }
            }
            if (cmd.args.containsKey(Param.meth)) {
                try {
                    MethHandle.get(cmd);
                } catch (Exception e) {
                    throw new XException("invalid method");
                }
            }
            if (cmd.args.containsKey(Param.anno)) {
                AnnotationsHandle.check(cmd);
            }
        } else {
            // can modify the weight in a upstream
            if (cmd.resource.parentResource.type != ResourceType.ups)
                throw new Exception(cmd.resource.parentResource.type.fullname + " does not contain " + ResourceType.sg.fullname);
            if (cmd.args.containsKey(Param.w)) {
                WeightHandle.check(cmd);
            }
            if (cmd.args.containsKey(Param.anno)) {
                AnnotationsHandle.check(cmd);
            }
        }
    }

    public static void checkServerGroup(Resource serverGroup) throws Exception {
        if (serverGroup.parentResource != null)
            throw new Exception(serverGroup.type.fullname + " is on top level");
    }

    public static List<String> names(Resource targetResource) throws Exception {
        if (targetResource == null) {
            return Application.get().serverGroupHolder.names();
        } else {
            return UpstreamHandle.get(targetResource).getServerGroupHandles()
                .stream().map(g -> g.alias).collect(Collectors.toList());
        }
    }

    public static List<ServerGroupRef> details(Resource targetResource) throws Exception {
        if (targetResource == null) {
            ServerGroupHolder holder = Application.get().serverGroupHolder;
            List<String> names = holder.names();
            List<ServerGroupRef> list = new LinkedList<>();
            for (String name : names) {
                list.add(new ServerGroupRef(name, holder.get(name)));
            }
            return list;
        } else {
            return UpstreamHandle.get(targetResource).getServerGroupHandles()
                .stream().map(h -> new ServerGroupRef(h.alias, h)).collect(Collectors.toList());
        }
    }

    public static void add(Command cmd) throws Exception {
        if (cmd.prepositionResource == null) {
            // add on top level
            if (!cmd.args.containsKey(Param.elg)) {
                cmd.args.put(Param.elg, Application.DEFAULT_CONTROL_EVENT_LOOP_GROUP_NAME);
            }

            String alias = cmd.resource.alias;
            String eventLoopGroupName = cmd.args.get(Param.elg);
            EventLoopGroup elg = EventLoopGroupHandle.get(eventLoopGroupName);
            HealthCheckConfig c = HealthCheckHandle.getHealthCheckConfig(cmd);
            Map<String, String> anno = null;
            if (cmd.args.containsKey(Param.anno)) {
                anno = AnnotationsHandle.get(cmd);
            }
            Application.get().serverGroupHolder.add(alias, elg, c, MethHandle.get(cmd), anno);
        } else {
            // add into upstream
            int weight = WeightHandle.get(cmd);
            var h = Application.get().upstreamHolder.get(cmd.prepositionResource.alias)
                .add(Application.get().serverGroupHolder.get(cmd.resource.alias), weight);
            if (cmd.args.containsKey(Param.anno)) {
                h.annotations = AnnotationsHandle.get(cmd);
            }
        }
    }

    public static void preCheck(Command cmd) throws Exception {
        if (cmd.prepositionResource != null)
            return; // it's ok to detach from upstream
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

    public static void forceRemove(Command cmd) throws Exception {
        if (cmd.prepositionResource == null) {
            // remove top level server group
            Application.get().serverGroupHolder.removeAndClear(cmd.resource.alias);
        } else {
            // detach from upstream
            ServerGroup g = Application.get().serverGroupHolder.get(cmd.resource.alias);
            UpstreamHandle.get(cmd.prepositionResource).remove(g);
        }
    }

    public static void update(Command cmd) throws Exception {
        if (cmd.resource.parentResource == null) {
            ServerGroup g = Application.get().serverGroupHolder.get(cmd.resource.alias);
            if (cmd.args.containsKey(Param.timeout)) {
                g.setHealthCheckConfig(HealthCheckHandle.getHealthCheckConfig(cmd));
            }
            if (cmd.args.containsKey(Param.meth)) {
                g.setMethod(MethHandle.get(cmd));
            }
            if (cmd.args.containsKey(Param.anno)) {
                g.setAnnotations(AnnotationsHandle.get(cmd));
            }
        } else {
            Upstream.ServerGroupHandle h = getHandle(cmd.resource);
            if (cmd.args.containsKey(Param.w)) {
                h.setWeight(WeightHandle.get(cmd));
            }
            if (cmd.args.containsKey(Param.anno)) {
                h.annotations = AnnotationsHandle.get(cmd);
            }
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
            Map<String, String> annos;
            if (h == null) {
                annos = Objects.requireNonNullElseGet(g.getAnnotations(), Map::of);
            } else {
                annos = Objects.requireNonNullElseGet(h.annotations, Map::of);
            }

            ObjectBuilder ob = new ObjectBuilder();
            for (Map.Entry<String, String> entry : annos.entrySet()) {
                ob.put(entry.getKey(), entry.getValue());
            }
            return ob.build().stringify();
        }
    }
}
