package vproxy.app.cmd.handle.resource;

import vproxy.app.Application;
import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;
import vproxy.app.cmd.Resource;
import vproxy.app.cmd.ResourceType;
import vproxy.util.Utils;
import vswitch.Switch;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class UserHandle {
    private UserHandle() {
    }

    public static void checkUser(Resource parent) throws Exception {
        if (parent == null)
            throw new Exception("cannot find " + ResourceType.user.fullname + " on top level");
        if (parent.type != ResourceType.sw)
            throw new Exception(parent.type.fullname + " does not contain " + ResourceType.user.fullname);
        SwitchHandle.checkSwitch(parent);
    }

    public static List<String> names(Resource parent) throws Exception {
        Switch sw = Application.get().switchHolder.get(parent.alias);
        return new ArrayList<>(sw.getUsers().keySet());
    }

    public static List<UserInfo> list(Resource parent) throws Exception {
        Switch sw = Application.get().switchHolder.get(parent.alias);
        return sw.getUsers().entrySet().stream().map(e -> new UserInfo(e.getKey(), e.getValue().vni)).collect(Collectors.toList());
    }

    public static void checkCreateUser(Command cmd) throws Exception {
        String pass = cmd.args.get(Param.pass);
        if (pass == null) {
            throw new Exception("missing " + Param.pass.fullname);
        }
        String vni = cmd.args.get(Param.vni);
        if (vni == null) {
            throw new Exception("missing " + Param.vni.fullname);
        }
        if (!Utils.isInteger(vni)) {
            throw new Exception("invalid " + Param.vni.fullname + ", not an integer");
        }
    }

    public static void add(Command cmd) throws Exception {
        String user = cmd.resource.alias;
        String pass = cmd.args.get(Param.pass);
        int vni = Integer.parseInt(cmd.args.get(Param.vni));
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.addUser(user, pass, vni);
    }

    public static void forceRemove(Command cmd) throws Exception {
        String user = cmd.resource.alias;
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.delUser(user);
    }

    public static class UserInfo {
        public final String name;
        public final int vni;

        public UserInfo(String name, int vni) {
            this.name = name;
            this.vni = vni;
        }

        @Override
        public String toString() {
            return name + " -> vni " + vni;
        }
    }
}
