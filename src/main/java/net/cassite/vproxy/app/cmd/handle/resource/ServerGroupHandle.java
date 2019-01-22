package net.cassite.vproxy.app.cmd.handle.resource;

import net.cassite.vproxy.app.Application;
import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.app.cmd.Param;
import net.cassite.vproxy.app.cmd.Resource;
import net.cassite.vproxy.app.cmd.ResourceType;
import net.cassite.vproxy.app.cmd.handle.param.HealthCheckHandle;
import net.cassite.vproxy.app.cmd.handle.param.MethHandle;
import net.cassite.vproxy.component.check.HealthCheckConfig;
import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.component.svrgroup.ServerGroup;
import net.cassite.vproxy.component.svrgroup.ServerGroups;

import java.util.List;
import java.util.stream.Collectors;

public class ServerGroupHandle {
    private ServerGroupHandle() {
    }

    public static ServerGroup get(Resource resource) throws Exception {
        if (resource.parentResource == null)
            return Application.get().serverGroupHolder.get(resource.alias);
        List<ServerGroup> ls = ServerGroupsHandle.get(resource.parentResource).getServerGroups();
        for (ServerGroup s : ls) {
            if (s.alias.equals(resource.alias))
                return s;
        }
        throw new NotFoundException();
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
        if (!cmd.args.containsKey(Param.elg))
            throw new Exception("missing argument " + Param.elg.fullname);
        // optional - if (!cmd.args.containsKey(Param.meth))
        // optional -     throw new Exception("missing argument " + Param.meth.fullname);
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
        }
    }

    public static void checkUpdateServerGroup(Command cmd) throws Exception {
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

    public static void add(Command cmd) throws Exception {
        if (cmd.prepositionResource == null) {
            String alias = cmd.resource.alias;
            String eventLoopGroupName = cmd.args.get(Param.elg);
            EventLoopGroup elg = EventLoopGroupHandle.get(eventLoopGroupName);
            HealthCheckConfig c = HealthCheckHandle.getHealthCheckConfig(cmd);
            ServerGroup g = Application.get().serverGroupHolder.add(alias, elg, c, MethHandle.get(cmd));
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
            if (groups.getServerGroups().contains(serverGroup)) {
                throw new Exception(ResourceType.sg + " " + serverGroup.alias + " is used by " + ResourceType.sgs + " " + groups.alias);
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
        ServerGroup g = Application.get().serverGroupHolder.get(cmd.resource.alias);
        if (cmd.args.containsKey(Param.timeout)) {
            g.setHealthCheckConfig(HealthCheckHandle.getHealthCheckConfig(cmd));
        }
        if (cmd.args.containsKey(Param.meth)) {
            g.setMethod(MethHandle.get(cmd));
        }
    }
}
