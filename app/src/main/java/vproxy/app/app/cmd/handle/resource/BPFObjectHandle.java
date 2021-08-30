package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Flag;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.ResourceType;
import vproxy.app.app.cmd.handle.param.BPFModeHandle;
import vproxy.base.util.Utils;
import vproxy.base.util.exception.XException;
import vproxy.vswitch.iface.XDPIface;
import vproxy.xdp.BPFMode;
import vproxy.xdp.BPFObject;

import java.util.ArrayList;
import java.util.List;

public class BPFObjectHandle {
    private BPFObjectHandle() {
    }

    public static BPFObject add(Command cmd) throws Exception {
        String nic = cmd.resource.alias;
        String path;
        if (cmd.args.containsKey(Param.path)) {
            path = Utils.filename(cmd.args.get(Param.path));
        } else {
            path = null;
        }
        String prog;
        prog = cmd.args.getOrDefault(Param.prog, BPFObject.DEFAULT_XDP_PROG_NAME);
        BPFMode mode = BPFModeHandle.get(cmd, BPFMode.SKB);
        boolean forceAttach = cmd.flags.contains(Flag.force);

        return Application.get().bpfObjectHolder.add(path, prog, nic, mode, forceAttach);
    }

    public static List<String> names() throws Exception {
        return Application.get().bpfObjectHolder.names();
    }

    public static List<BPFObject> list() throws Exception {
        var bpfObjectHolder = Application.get().bpfObjectHolder;
        List<BPFObject> ls = new ArrayList<>();
        for (var name : bpfObjectHolder.names()) {
            ls.add(bpfObjectHolder.get(name));
        }
        return ls;
    }

    public static void preRemoveCheck(Command cmd) throws Exception {
        var bpfObject = Application.get().bpfObjectHolder.get(cmd.resource.alias);
        var swNames = Application.get().switchHolder.names();
        for (var swName : swNames) {
            var sw = Application.get().switchHolder.get(swName);
            var ifaces = sw.getIfaces();
            for (var iface : ifaces) {
                if (!(iface instanceof XDPIface)) {
                    continue;
                }
                var xdp = (XDPIface) iface;
                if (xdp.bpfMap.bpfObject == bpfObject) {
                    throw new XException(ResourceType.bpfobj.fullname + " " + bpfObject.nic
                        + " is used by " + ResourceType.xdp.fullname + " " + xdp.nic
                        + " in " + ResourceType.sw.fullname + " " + sw.alias);
                }
            }
        }
    }

    public static void remove(Command cmd) throws Exception {
        Application.get().bpfObjectHolder.removeAndRelease(cmd.resource.alias, true);
    }
}
