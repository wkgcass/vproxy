package vproxy.app.cmd.handle.resource;

import vproxy.app.Application;
import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;
import vproxy.app.cmd.Resource;
import vproxy.app.cmd.ResourceType;
import vswitch.Switch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    public static List<String> list(Resource parent) throws Exception {
        Switch sw = Application.get().switchHolder.get(parent.alias);
        return new ArrayList<>(sw.getUsers().keySet());
    }

    public static void checkCreateUser(Command cmd) throws Exception {
        String pass = cmd.args.get(Param.pass);
        if (pass == null) {
            throw new Exception("missing " + Param.pass);
        }
    }

    public static void add(Command cmd) throws Exception {
        String user = cmd.resource.alias;
        String pass = cmd.args.get(Param.pass);
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.addUser(user, pass);
    }

    public static void forceRemove(Command cmd) throws Exception {
        String user = cmd.resource.alias;
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.delUser(user);
    }
}
