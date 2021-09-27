package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.vswitch.util.UserInfo;
import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.app.app.cmd.Resource;
import io.vproxy.app.app.cmd.ResourceType;
import io.vproxy.app.app.cmd.handle.param.FloodHandle;
import io.vproxy.app.app.cmd.handle.param.MTUHandle;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.vswitch.Switch;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class UserHandle {
    private UserHandle() {
    }

    public static List<String> names(Resource parent) throws Exception {
        Switch sw = Application.get().switchHolder.get(parent.alias);
        return new ArrayList<>(sw.getUsers().keySet());
    }

    public static List<UserInfo> list(Resource parent) throws Exception {
        Switch sw = Application.get().switchHolder.get(parent.alias);
        return sw.getUsers().entrySet().stream().map(e -> new UserInfo(e.getKey(), e.getValue())).collect(Collectors.toList());
    }

    public static void add(Command cmd) throws Exception {
        String user = cmd.resource.alias;
        String pass = cmd.args.get(Param.pass);
        int vni = Integer.parseInt(cmd.args.get(Param.vni));
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);

        Integer defaultMtu = null;
        if (cmd.args.containsKey(Param.mtu)) {
            defaultMtu = MTUHandle.get(cmd);
        }
        Boolean defaultFloodAllowed = null;
        if (cmd.args.containsKey(Param.flood)) {
            defaultFloodAllowed = FloodHandle.get(cmd);
        }

        sw.addUser(user, pass, vni, defaultMtu, defaultFloodAllowed);
    }

    public static void update(Command cmd) throws Exception {
        Switch sw = Application.get().switchHolder.get(cmd.resource.parentResource.alias);
        var opt = sw.getUsers().entrySet().stream().filter(e -> e.getKey().equals(cmd.resource.alias)).findFirst();
        if (opt.isEmpty()) {
            throw new NotFoundException(ResourceType.user.fullname, cmd.resource.alias);
        }
        io.vproxy.vswitch.util.UserInfo info = opt.get().getValue();

        if (cmd.args.containsKey(Param.mtu)) {
            info.defaultMtu = MTUHandle.get(cmd);
        }
        if (cmd.args.containsKey(Param.flood)) {
            info.defaultFloodAllowed = FloodHandle.get(cmd);
        }
    }

    public static void remove(Command cmd) throws Exception {
        String user = cmd.resource.alias;
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.delUser(user);
    }

    public static class UserInfo {
        public final String name;
        public final io.vproxy.vswitch.util.UserInfo info;

        public UserInfo(String name, io.vproxy.vswitch.util.UserInfo info) {
            this.name = name;
            this.info = info;
        }

        @Override
        public String toString() {
            return name + " -> vni " + info.vni
                + " mtu " + info.defaultMtu
                + " flood " + (info.defaultFloodAllowed ? "allow" : "deny");
        }
    }
}
