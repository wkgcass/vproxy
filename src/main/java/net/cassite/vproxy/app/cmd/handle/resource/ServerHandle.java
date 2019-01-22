package net.cassite.vproxy.app.cmd.handle.resource;

import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.app.cmd.Resource;
import net.cassite.vproxy.app.cmd.handle.param.AddrHandle;
import net.cassite.vproxy.app.cmd.handle.param.IpHandle;
import net.cassite.vproxy.app.cmd.handle.param.WeightHandle;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.component.svrgroup.ServerGroup;
import net.cassite.vproxy.util.Utils;

import java.util.List;
import java.util.stream.Collectors;

public class ServerHandle {
    private ServerHandle() {
    }

    public static void checkCreateServer(Command cmd) throws Exception {
        AddrHandle.check(cmd);
        IpHandle.check(cmd);
        WeightHandle.check(cmd);
    }

    public static void checkUpdateServer(Command cmd) throws Exception {
        WeightHandle.check(cmd);
    }

    public static List<String> names(Resource parent) throws Exception {
        return detail(parent).stream().map(ref -> ref.h.alias).collect(Collectors.toList());
    }

    public static List<ServerRef> detail(Resource parent) throws Exception {
        return ServerGroupHandle.get(parent)
            .getServerHealthHandles()
            .stream().map(ServerRef::new)
            .collect(Collectors.toList());
    }

    public static void add(Command cmd) throws Exception {
        String name = cmd.resource.alias;

        ServerGroupHandle.get(cmd.prepositionResource)
            .add(name, AddrHandle.get(cmd), IpHandle.get(cmd), WeightHandle.get(cmd));
    }

    public static void forceRemove(Command cmd) throws Exception {
        ServerGroupHandle.get(cmd.prepositionResource)
            .remove(cmd.resource.alias);
    }

    public static void update(Command cmd) throws Exception {
        for (ServerGroup.ServerHealthHandle h : ServerGroupHandle.get(cmd.prepositionResource).getServerHealthHandles()) {
            if (h.alias.equals(cmd.resource.alias)) {
                h.setWeight(WeightHandle.get(cmd));
                return;
            }
        }
        throw new NotFoundException();
    }

    public static class ServerRef {
        public final ServerGroup.ServerHealthHandle h;

        public ServerRef(ServerGroup.ServerHealthHandle h) {
            this.h = h;
        }

        @Override
        public String toString() {
            return h.alias + " -> connect to " + Utils.ipStr(h.server.getAddress().getAddress()) + ":" + h.server.getPort()
                + " via " + Utils.ipStr(h.local.getAddress()) + " weight " + h.getWeight()
                + " currently " + (h.healthy ? "UP" : "DOWN");
        }
    }
}
