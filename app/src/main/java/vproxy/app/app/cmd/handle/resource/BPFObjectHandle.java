package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Flag;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.handle.param.BPFModeHandle;
import vproxy.base.util.Utils;
import vproxy.xdp.BPFMode;
import vproxy.xdp.BPFObject;

import java.util.ArrayList;
import java.util.List;

public class BPFObjectHandle {
    private BPFObjectHandle() {
    }

    public static void add(Command cmd) throws Exception {
        String nic = cmd.resource.alias;
        String path = Utils.filename(cmd.args.get(Param.path));
        String prog = cmd.args.get(Param.prog);
        BPFMode mode = BPFModeHandle.get(cmd, BPFMode.SKB);
        boolean forceAttach = cmd.flags.contains(Flag.force);

        Application.get().bpfObjectHolder.add(path, prog, nic, mode, forceAttach);
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

    public static void remove(Command cmd) throws Exception {
        Application.get().bpfObjectHolder.removeAndRelease(cmd.resource.alias);
    }
}
