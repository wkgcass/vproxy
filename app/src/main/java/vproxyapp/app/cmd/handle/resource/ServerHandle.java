package vproxyapp.app.cmd.handle.resource;

import vproxyapp.app.cmd.Command;
import vproxyapp.app.cmd.Param;
import vproxyapp.app.cmd.Resource;
import vproxyapp.app.cmd.ResourceType;
import vproxyapp.app.cmd.handle.param.AddrHandle;
import vproxyapp.app.cmd.handle.param.WeightHandle;
import vproxybase.util.exception.NotFoundException;
import vproxybase.component.svrgroup.ServerGroup;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ServerHandle {
    private ServerHandle() {
    }

    public static void checkServer(Resource server) throws Exception {
        if (server.parentResource == null)
            throw new Exception("cannot find " + server.type.fullname + " on top level");
        if (server.parentResource.type != ResourceType.sg)
            throw new Exception(server.parentResource.type.fullname + " does not contain " + server.type.fullname);
        ServerGroupHandle.checkServerGroup(server.parentResource);
    }

    public static void checkCreateServer(Command cmd) throws Exception {
        AddrHandle.check(cmd);
        WeightHandle.check(cmd);
    }

    public static void checkUpdateServer(Command cmd) throws Exception {
        WeightHandle.check(cmd);
    }

    public static ServerGroup.ServerHandle get(Resource server) throws Exception {
        String alias = server.alias;
        ServerGroup grp = ServerGroupHandle.get(server.parentResource);
        Optional<ServerGroup.ServerHandle> opt = grp.getServerHandles().stream().filter(s -> s.alias.equals(alias)).findFirst();
        return opt.orElseThrow(() -> new NotFoundException("server in server-group " + server.parentResource.alias, server.alias));
    }

    public static List<String> names(Resource parent) throws Exception {
        return detail(parent).stream().map(ref -> ref.h.alias).collect(Collectors.toList());
    }

    public static List<ServerRef> detail(Resource parent) throws Exception {
        return ServerGroupHandle.get(parent)
            .getServerHandles()
            .stream().map(ServerRef::new)
            .collect(Collectors.toList());
    }

    public static void add(Command cmd) throws Exception {
        String name = cmd.resource.alias;

        String host;
        {
            String addr = cmd.args.get(Param.addr);
            host = addr.substring(0, addr.lastIndexOf(":"));
        }
        // no need to check whether host is an ip
        // will be check in `group.add()`

        ServerGroupHandle.get(cmd.prepositionResource)
            .add(name, host, AddrHandle.get(cmd), WeightHandle.get(cmd));
    }

    public static void forceRemove(Command cmd) throws Exception {
        ServerGroupHandle.get(cmd.prepositionResource)
            .remove(cmd.resource.alias);
    }

    public static void update(Command cmd) throws Exception {
        for (ServerGroup.ServerHandle h : ServerGroupHandle.get(cmd.resource.parentResource).getServerHandles()) {
            if (h.alias.equals(cmd.resource.alias)) {
                h.setWeight(WeightHandle.get(cmd));
                return;
            }
        }
        throw new NotFoundException("server in server-group " + cmd.resource.parentResource.alias, cmd.resource.alias);
    }

    public static class ServerRef {
        public final ServerGroup.ServerHandle h;

        public ServerRef(ServerGroup.ServerHandle h) {
            this.h = h;
        }

        @Override
        public String toString() {
            return h.toString();
        }
    }
}
