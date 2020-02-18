package vproxy.app.cmd.handle.resource;

import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;
import vproxy.app.cmd.Resource;
import vproxy.app.cmd.ResourceType;
import vproxy.app.cmd.handle.param.AddrHandle;
import vproxy.app.cmd.handle.param.WeightHandle;
import vproxy.component.exception.NotFoundException;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.util.Utils;

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
            /*
             * e.g. with host
             * google -> host google.com connect-to 216.58.197.238:443 weight 10 currently UP
             * or without host
             * google -> connect-to 216.58.197.238:443 weight 10 currently UP
             * or for logic deleted: add * before alias
             * *google -> host google.com connect-to 216.58.197.238:443 weight 10 currently UP
             */
            return (h.isLogicDelete() ? "*" : "") + h.alias + " ->"
                + (h.hostName == null ? "" : " host " + h.hostName /* now connected to */)
                + " connect-to " + Utils.ipStr(h.server.getAddress().getAddress()) + ":" + h.server.getPort()
                + " weight " + h.getWeight()
                + " currently " + (h.healthy ? "UP" : "DOWN")
                + " cost " + h.getHcCost();
        }
    }
}
