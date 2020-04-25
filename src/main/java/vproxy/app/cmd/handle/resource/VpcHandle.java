package vproxy.app.cmd.handle.resource;

import vproxy.app.Application;
import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;
import vproxy.app.cmd.Resource;
import vproxy.app.cmd.ResourceType;
import vproxy.app.cmd.handle.param.NetworkHandle;
import vproxy.util.Network;
import vproxy.util.Tuple;
import vproxy.util.Utils;
import vswitch.Switch;
import vswitch.Table;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VpcHandle {
    private VpcHandle() {
    }

    public static void checkVpc(Resource parent) throws Exception {
        if (parent == null)
            throw new Exception("cannot find " + ResourceType.vpc.fullname + " on top level");
        if (parent.type != ResourceType.sw)
            throw new Exception(parent.type.fullname + " does not contain " + ResourceType.vpc.fullname);
        SwitchHandle.checkSwitch(parent);
    }

    public static void checkVpcName(Resource self) throws Exception {
        String vpc = self.alias;
        try {
            Integer.parseInt(vpc);
        } catch (NumberFormatException e) {
            throw new Exception("vpc name should be an integer representing the vni");
        }
    }

    public static Table get(Resource self) throws Exception {
        int vpc = Integer.parseInt(self.alias);
        Switch sw = SwitchHandle.get(self.parentResource);
        return sw.getTable(vpc);
    }

    public static void checkCreateVpc(Command cmd) throws Exception {
        checkVpc(cmd.prepositionResource);
        checkVpcName(cmd.resource);
        if (!cmd.args.containsKey(Param.v4net)) {
            throw new Exception("missing argument " + Param.v4net);
        }
        String v4net = cmd.args.get(Param.v4net);
        if (!Utils.validNetworkStr(v4net)) {
            throw new Exception("invalid argument " + Param.v4net + ": " + v4net);
        }
        String n = v4net.split("/")[0];
        if (Utils.parseIpString(n).length != 4) {
            throw new Exception("invalid argument " + Param.v4net + ": not ipv4 network" + ": " + v4net);
        }
        if (cmd.args.containsKey(Param.v6net)) {
            String v6net = cmd.args.get(Param.v6net);
            if (!Utils.validNetworkStr(v6net)) {
                throw new Exception("invalid argument " + Param.v6net + ": " + v6net);
            }
            n = v6net.split("/")[0];
            if (Utils.parseIpString(n).length != 16) {
                throw new Exception("invalid argument " + Param.v6net + ": not ipv6 network" + ": " + v6net);
            }
        }
    }

    public static void add(Command cmd) throws Exception {
        Switch sw = SwitchHandle.get(cmd.prepositionResource);
        Tuple<byte[], byte[]> v4 = NetworkHandle.get(cmd.args.get(Param.v4net));
        Network v4net = new Network(v4.left, v4.right);
        Network v6net = null;
        if (cmd.args.containsKey(Param.v6net)) {
            Tuple<byte[], byte[]> v6 = NetworkHandle.get(cmd.args.get(Param.v6net));
            v6net = new Network(v6.left, v6.right);
        }
        sw.addTable(Integer.parseInt(cmd.resource.alias), v4net, v6net);
    }

    public static void forceRemove(Command cmd) throws Exception {
        Switch sw = SwitchHandle.get(cmd.prepositionResource);
        sw.delTable(Integer.parseInt(cmd.resource.alias));
    }

    public static List<VpcEntry> list(Resource parent) throws Exception {
        Switch sw = Application.get().switchHolder.get(parent.alias);
        var tables = sw.getTables().values();

        List<VpcEntry> ls = new ArrayList<>();
        for (var tbl : tables) {
            ls.add(new VpcEntry(tbl.vni, tbl.v4network, tbl.v6network));
        }
        ls.sort(Comparator.comparingInt(a -> a.vpc));
        return ls;
    }

    public static class VpcEntry {
        public final int vpc;
        public final Network v4network;
        public final Network v6network;

        public VpcEntry(int vpc, Network v4network, Network v6network) {
            this.vpc = vpc;
            this.v4network = v4network;
            this.v6network = v6network;
        }

        @Override
        public String toString() {
            return vpc + " -> v4network " + v4network + (v6network != null ? (" v6network " + v6network) : "");
        }
    }
}
