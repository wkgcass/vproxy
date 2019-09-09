package vproxy.app.cmd.handle.resource;

import vproxy.app.Application;
import vproxy.app.ServerGroupHolder;
import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;
import vproxy.app.cmd.Resource;
import vproxy.app.cmd.ResourceType;
import vproxy.app.cmd.handle.param.HealthCheckHandle;
import vproxy.app.cmd.handle.param.MethHandle;
import vproxy.app.cmd.handle.param.WeightHandle;
import vproxy.component.auto.SmartGroupDelegate;
import vproxy.component.check.HealthCheckConfig;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.exception.NotFoundException;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.component.svrgroup.ServerGroups;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class ServerGroupHandle {
    private ServerGroupHandle() {
    }

    public static ServerGroup get(Resource resource) throws Exception {
        if (resource.parentResource == null)
            return Application.get().serverGroupHolder.get(resource.alias);
        List<ServerGroups.ServerGroupHandle> ls = ServerGroupsHandle.get(resource.parentResource).getServerGroups();
        for (ServerGroups.ServerGroupHandle s : ls) {
            if (s.alias.equals(resource.alias))
                return s.group;
        }
        throw new NotFoundException("server-group in server-groups " + resource.parentResource.alias, resource.alias);
    }

    public static ServerGroups.ServerGroupHandle getHandle(Resource resource) throws Exception {
        List<ServerGroups.ServerGroupHandle> ls = ServerGroupsHandle.get(resource.parentResource).getServerGroups();
        for (ServerGroups.ServerGroupHandle s : ls) {
            if (s.alias.equals(resource.alias))
                return s;
        }
        throw new NotFoundException("server-group in server-groups " + resource.parentResource.alias, resource.alias);
    }

    public static void checkAttachServerGroup(Command cmd) throws Exception {
        if (cmd.args.containsKey(Param.w))
            WeightHandle.check(cmd);
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
            throw new Exception("invalid health check config");
        }
        if (cmd.args.containsKey(Param.meth)) {
            try {
                MethHandle.get(cmd);
            } catch (Exception e) {
                throw new Exception("invalid method");
            }
        } else {
            cmd.args.put(Param.meth, "wrr");
        }
    }

    public static void checkUpdateServerGroup(Command cmd) throws Exception {
        if (cmd.resource.parentResource == null) {
            // can only update the server group self info on top level
            // i'm not saying that you cannot modify the one in serverGroups
            // but you don't have to go into serverGroups to modify,
            // the one on top level is the same one in any serverGroup
            if (cmd.args.containsKey(Param.timeout)
                || cmd.args.containsKey(Param.period)
                || cmd.args.containsKey(Param.up)
                || cmd.args.containsKey(Param.down)) {
                try {
                    HealthCheckHandle.getHealthCheckConfig(cmd);
                } catch (Exception e) {
                    throw new Exception("missing health check argument or is invalid");
                }
            }
            if (cmd.args.containsKey(Param.meth)) {
                try {
                    MethHandle.get(cmd);
                } catch (Exception e) {
                    throw new Exception("invalid method");
                }
            }
        } else {
            // can modify the weight in a ServerGroups
            if (cmd.resource.parentResource.type != ResourceType.sgs)
                throw new Exception(cmd.resource.parentResource.type.fullname + " does not contain " + ResourceType.sg.fullname);
            if (cmd.args.containsKey(Param.w)) {
                WeightHandle.check(cmd);
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
            return ServerGroupsHandle.get(targetResource).getServerGroups()
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
            return ServerGroupsHandle.get(targetResource).getServerGroups()
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
            Application.get().serverGroupHolder.add(alias, elg, c, MethHandle.get(cmd));
        } else {
            // add into serverGroups
            int weight = WeightHandle.get(cmd);
            Application.get().serverGroupsHolder.get(cmd.prepositionResource.alias)
                .add(Application.get().serverGroupHolder.get(cmd.resource.alias), weight);
        }
    }

    public static void preCheck(Command cmd) throws Exception {
        if (cmd.prepositionResource != null)
            return; // it's ok to detach from serverGroups
        // remove top level server group
        ServerGroup serverGroup = Application.get().serverGroupHolder.get(cmd.resource.alias);

        // check serverGroups
        for (String groupsName : Application.get().serverGroupsHolder.names()) {
            ServerGroups groups = Application.get().serverGroupsHolder.get(groupsName);
            if (groups.getServerGroups().stream().anyMatch(h -> h.group.equals(serverGroup))) {
                throw new Exception(ResourceType.sg.fullname + " " + serverGroup.alias + " is used by " + ResourceType.sgs.fullname + " " + groups.alias);
            }
        }
        // check smart-group-delegate
        for (String slgName : Application.get().smartGroupDelegateHolder.names()) {
            SmartGroupDelegate slg = Application.get().smartGroupDelegateHolder.get(slgName);
            if (slg.handledGroup.equals(serverGroup)) {
                throw new Exception(ResourceType.sgd.fullname + " " + serverGroup.alias + " is used by " + ResourceType.sgd.fullname + " " + slg.alias);
            }
        }
    }

    public static void forceRemove(Command cmd) throws Exception {
        if (cmd.prepositionResource == null) {
            // remove top level server group
            Application.get().serverGroupHolder.removeAndClear(cmd.resource.alias);
        } else {
            // detach from serverGroups
            ServerGroup g = Application.get().serverGroupHolder.get(cmd.resource.alias);
            ServerGroupsHandle.get(cmd.prepositionResource).remove(g);
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
        } else {
            ServerGroups.ServerGroupHandle h = getHandle(cmd.resource);
            if (cmd.args.containsKey(Param.w)) {
                h.setWeight(WeightHandle.get(cmd));
            }
        }
    }

    public static class ServerGroupRef {
        private final String alias;
        private final ServerGroup g;
        private final ServerGroups.ServerGroupHandle h;

        public ServerGroupRef(String alias, ServerGroup g) {
            this.alias = alias;
            this.g = g;
            this.h = null;
        }

        public ServerGroupRef(String alias, ServerGroups.ServerGroupHandle h) {
            this.alias = alias;
            this.h = h;
            this.g = h.group;
        }

        @Override
        public String toString() {
            HealthCheckConfig c = g.getHealthCheckConfig();
            return alias + " -> timeout " + c.timeout + " period " + c.period +
                " up " + c.up + " down " + c.down + " method " + g.getMethod() +
                " event-loop-group " + g.eventLoopGroup.alias +
                (h == null ? "" : " weight " + h.getWeight());
        }
    }
}
