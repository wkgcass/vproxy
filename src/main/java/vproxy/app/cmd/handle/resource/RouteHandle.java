package vproxy.app.cmd.handle.resource;

import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;
import vproxy.app.cmd.Resource;
import vproxy.app.cmd.ResourceType;
import vproxy.app.cmd.handle.param.NetworkHandle;
import vproxy.util.Network;
import vproxy.util.Utils;
import vswitch.RouteTable;
import vswitch.Table;

import java.util.List;
import java.util.stream.Collectors;

public class RouteHandle {
    private RouteHandle() {
    }

    public static void checkRoute(Resource parent) throws Exception {
        if (parent == null)
            throw new Exception("cannot find " + ResourceType.route.fullname + " on top level");
        if (parent.type != ResourceType.vpc) {
            if (parent.type == ResourceType.sw) {
                throw new Exception(parent.type.fullname + " does not directly contain " + ResourceType.route.fullname + ", you have to specify vpc first");
            }
            throw new Exception(parent.type.fullname + " does not contain " + ResourceType.route.fullname);
        }

        VpcHandle.checkVpc(parent.parentResource);
        VpcHandle.checkVpcName(parent);
    }

    public static List<String> names(Resource parent) throws Exception {
        Table tbl = VpcHandle.get(parent);
        return tbl.routeTable.getRules().stream().map(r -> r.alias).collect(Collectors.toList());
    }

    public static List<RouteTable.RouteRule> list(Resource parent) throws Exception {
        Table tbl = VpcHandle.get(parent);
        return tbl.routeTable.getRules();
    }

    public static void checkCreateRoute(Command cmd) throws Exception {
        String net = cmd.args.get(Param.net);
        if (net == null) {
            throw new Exception("missing " + Param.net.fullname);
        }
        NetworkHandle.check(cmd);
        String vni = cmd.args.get(Param.vni);
        if (vni == null) {
            throw new Exception("missing " + Param.vni.fullname);
        }
        if (!Utils.isInteger(vni)) {
            throw new Exception("invalid argument for " + Param.vni + ": should be an integer");
        }
    }

    public static void add(Command cmd) throws Exception {
        String alias = cmd.resource.alias;
        Network net = NetworkHandle.get(cmd);
        int vni = Integer.parseInt(cmd.args.get(Param.vni));

        Table tbl = VpcHandle.get(cmd.prepositionResource);
        tbl.routeTable.addRule(new RouteTable.RouteRule(alias, net, vni));
    }

    public static void forceRemove(Command cmd) throws Exception {
        String alias = cmd.resource.alias;

        Table tbl = VpcHandle.get(cmd.prepositionResource);
        tbl.routeTable.delRule(alias);
    }
}
