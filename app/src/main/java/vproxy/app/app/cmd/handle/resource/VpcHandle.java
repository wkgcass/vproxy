package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.Resource;
import vproxy.app.app.cmd.handle.param.AnnotationsHandle;
import vproxy.app.app.cmd.handle.param.NetworkHandle;
import vproxy.base.util.Annotations;
import vproxy.base.util.Network;
import vproxy.vswitch.Switch;
import vproxy.vswitch.VirtualNetwork;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VpcHandle {
    private VpcHandle() {
    }

    public static void checkVpcName(Resource resource) throws Exception {
        String vpc = resource.alias;
        try {
            Integer.parseInt(vpc);
        } catch (NumberFormatException e) {
            throw new Exception("vpc name should be an integer representing the vni");
        }
    }

    public static VirtualNetwork get(Resource self) throws Exception {
        int vpc = Integer.parseInt(self.alias);
        Switch sw = SwitchHandle.get(self.parentResource);
        return sw.getNetwork(vpc);
    }

    public static void add(Command cmd) throws Exception {
        Switch sw = SwitchHandle.get(cmd.prepositionResource);
        Network v4net = NetworkHandle.get(cmd.args.get(Param.v4net));
        Network v6net = null;
        if (cmd.args.containsKey(Param.v6net)) {
            v6net = NetworkHandle.get(cmd.args.get(Param.v6net));
        }
        Annotations annotations = null;
        if (cmd.args.containsKey(Param.anno)) {
            annotations = AnnotationsHandle.get(cmd);
        }
        sw.addNetwork(Integer.parseInt(cmd.resource.alias), v4net, v6net, annotations);
    }

    public static void remove(Command cmd) throws Exception {
        Switch sw = SwitchHandle.get(cmd.prepositionResource);
        sw.delNetwork(Integer.parseInt(cmd.resource.alias));
    }

    public static List<VpcEntry> list(Resource parentResource) throws Exception {
        Switch sw = Application.get().switchHolder.get(parentResource.alias);
        var networks = sw.getNetworks().values();

        List<VpcEntry> ls = new ArrayList<>();
        for (var net : networks) {
            ls.add(new VpcEntry(net.vni, net.v4network, net.v6network, net.getAnnotations()));
        }
        ls.sort(Comparator.comparingInt(a -> a.vpc));
        return ls;
    }

    public static class VpcEntry {
        public final int vpc;
        public final Network v4network;
        public final Network v6network;
        public final Annotations annotations;

        public VpcEntry(int vpc, Network v4network, Network v6network, Annotations annotations) {
            this.vpc = vpc;
            this.v4network = v4network;
            this.v6network = v6network;
            this.annotations = annotations;
        }

        @Override
        public String toString() {
            return vpc + " -> v4network " + v4network
                + (v6network != null ? (" v6network " + v6network) : "")
                + (!annotations.isEmpty() ? (" annotations " + annotations) : "");
        }
    }
}
