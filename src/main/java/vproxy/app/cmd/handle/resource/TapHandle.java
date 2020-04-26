package vproxy.app.cmd.handle.resource;

import vproxy.app.Application;
import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;
import vproxy.app.cmd.Resource;
import vproxy.app.cmd.ResourceType;
import vproxy.component.exception.XException;
import vproxy.util.Utils;
import vswitch.Switch;
import vswitch.util.Iface;

import java.util.List;
import java.util.stream.Collectors;

public class TapHandle {
    private TapHandle() {
    }

    public static void checkTap(Resource parent) throws Exception {
        if (parent == null)
            throw new Exception("cannot find " + ResourceType.tap.fullname + " on top level");
        if (parent.type != ResourceType.sw)
            throw new Exception(parent.type.fullname + " does not contain " + ResourceType.tap.fullname);
        SwitchHandle.checkSwitch(parent);
    }

    public static List<String> names(Resource parent) throws Exception {
        Switch sw = Application.get().switchHolder.get(parent.alias);
        return sw.getIfaces().stream().filter(i -> i.tap != null).map(i -> i.tap.tuntap.dev).collect(Collectors.toList());
    }

    public static List<TapInfo> list(Resource parent) throws Exception {
        Switch sw = Application.get().switchHolder.get(parent.alias);
        return sw.getIfaces().stream().filter(i -> i.tap != null).map(TapInfo::new).collect(Collectors.toList());
    }

    public static void checkCreateTap(Command cmd) throws Exception {
        String devPattern = cmd.resource.alias;
        if (devPattern.length() > 10) {
            throw new XException("tap dev name pattern too long: should <= 10");
        }
        String vni = cmd.args.get(Param.vni);
        if (vni == null) {
            throw new Exception("missing " + Param.vni.fullname);
        }
        if (!Utils.isInteger(vni)) {
            throw new Exception("invalid " + Param.vni.fullname + ", not an integer");
        }
    }

    public static String add(Command cmd) throws Exception {
        String devPattern = cmd.resource.alias;
        int vni = Integer.parseInt(cmd.args.get(Param.vni));
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        return sw.addTap(devPattern, vni);
    }

    public static void forceRemove(Command cmd) throws Exception {
        String devPattern = cmd.resource.alias;
        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);
        sw.delTap(devPattern);
    }

    public static class TapInfo {
        public final Iface iface;

        public TapInfo(Iface iface) {
            this.iface = iface;
        }

        @Override
        public String toString() {
            assert iface.tap != null;
            return iface.tap.tuntap.dev + " -> vni " + iface.serverSideVni;
        }
    }
}
