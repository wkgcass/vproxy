package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.Resource;
import vproxy.app.app.cmd.ResourceType;
import vproxy.app.app.cmd.handle.param.AnnotationsHandle;
import vproxy.app.app.cmd.handle.param.FloodHandle;
import vproxy.app.app.cmd.handle.param.MTUHandle;
import vproxy.app.app.cmd.handle.param.MacHandle;
import vproxy.base.util.Annotations;
import vproxy.base.util.Utils;
import vproxy.base.util.exception.XException;
import vproxy.vfd.MacAddress;
import vproxy.vswitch.Switch;

public class TunHandle {
    private TunHandle() {
    }

    public static void checkTunParent(Resource parent) throws Exception {
        if (parent == null)
            throw new Exception("cannot find " + ResourceType.tun.fullname + " on top level");
        if (parent.type != ResourceType.sw)
            throw new Exception(parent.type.fullname + " does not contain " + ResourceType.tun.fullname);
        SwitchHandle.checkSwitch(parent);
    }

    public static void checkCreateTun(Command cmd) throws Exception {
        String devPattern = cmd.resource.alias;
        if (devPattern.length() > 15) {
            throw new XException("tun dev name pattern too long: should <= 15");
        }
        String vni = cmd.args.get(Param.vni);
        if (vni == null) {
            throw new Exception("missing " + Param.vni.fullname);
        }
        if (!Utils.isInteger(vni)) {
            throw new Exception("invalid " + Param.vni.fullname + ", not an integer");
        }
        MacHandle.check(cmd);
        if (cmd.args.containsKey(Param.mtu)) {
            MTUHandle.check(cmd);
        }
        if (cmd.args.containsKey(Param.flood)) {
            FloodHandle.check(cmd);
        }
    }

    public static String add(Command cmd) throws Exception {
        String devPattern = cmd.resource.alias;
        int vni = Integer.parseInt(cmd.args.get(Param.vni));
        MacAddress mac = MacHandle.get(cmd);
        String postScript = cmd.args.get(Param.postscript);
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        Annotations anno = null;
        if (cmd.args.containsKey(Param.anno)) {
            anno = AnnotationsHandle.get(cmd);
        }
        Integer mtu = null;
        if (cmd.args.containsKey(Param.mtu)) {
            mtu = MTUHandle.get(cmd);
        }
        Boolean flood = null;
        if (cmd.args.containsKey(Param.flood)) {
            flood = FloodHandle.get(cmd);
        }
        return sw.addTun(devPattern, vni, mac, postScript, anno, mtu, flood);
    }

    public static void forceRemove(Command cmd) throws Exception {
        String devPattern = cmd.resource.alias;
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.delTun(devPattern);
    }
}
