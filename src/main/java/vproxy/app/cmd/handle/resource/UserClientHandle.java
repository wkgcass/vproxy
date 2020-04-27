package vproxy.app.cmd.handle.resource;

import vproxy.app.Application;
import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;
import vproxy.app.cmd.Resource;
import vproxy.app.cmd.ResourceType;
import vproxy.app.cmd.handle.param.AddrHandle;
import vproxy.component.exception.XException;
import vproxy.util.Utils;
import vswitch.Switch;

import java.net.InetSocketAddress;

public class UserClientHandle {
    private UserClientHandle() {
    }

    public static void checkUserClient(Resource parent) throws Exception {
        if (parent == null)
            throw new Exception("cannot find " + ResourceType.ucli.fullname + " on top level");
        if (parent.type != ResourceType.sw)
            throw new Exception(parent.type.fullname + " does not contain " + ResourceType.ucli.fullname);
        SwitchHandle.checkSwitch(parent);
    }

    public static void checkCreateUserClient(Command cmd) throws Exception {
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
        AddrHandle.check(cmd);
    }

    public static void add(Command cmd) throws Exception {
        String user = cmd.resource.alias;
        String pass = cmd.args.get(Param.pass);
        int vni = Integer.parseInt(cmd.args.get(Param.vni));
        InetSocketAddress addr = AddrHandle.get(cmd);
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.addUserClient(user, pass, vni, addr);
    }

    public static void checkRemoveUserClient(Command cmd) throws Exception {
        AddrHandle.check(cmd);
    }

    public static void forceRemove(Command cmd) throws Exception {
        String user = cmd.resource.alias;
        InetSocketAddress addr = AddrHandle.get(cmd);
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.delUserClient(user, addr);
    }
}
