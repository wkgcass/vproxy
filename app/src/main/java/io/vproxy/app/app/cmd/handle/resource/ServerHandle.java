package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.app.app.cmd.Resource;
import io.vproxy.app.app.cmd.handle.param.AddrHandle;
import io.vproxy.app.app.cmd.handle.param.WeightHandle;
import io.vproxy.base.component.svrgroup.ServerGroup;
import io.vproxy.base.util.exception.NotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ServerHandle {
    private ServerHandle() {
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

    public static void remove(Command cmd) throws Exception {
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
