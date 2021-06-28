package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.Resource;
import vproxy.app.app.cmd.handle.param.NetworkHandle;
import vproxy.base.util.Network;
import vproxy.base.util.Utils;
import vproxy.vfd.IP;
import vproxy.vswitch.RouteTable;
import vproxy.vswitch.Table;

import java.util.List;
import java.util.stream.Collectors;

public class RouteHandle {
    private RouteHandle() {
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
        String ip = cmd.args.get(Param.via);
        if (vni == null && ip == null) {
            throw new Exception("missing " + Param.vni.fullname + " or " + Param.via.fullname);
        }
        if (vni != null && ip != null) {
            throw new Exception("cannot specify " + Param.vni.fullname + " and " + Param.via.fullname + " at the same time");
        }
        if (vni != null && !Utils.isInteger(vni)) {
            throw new Exception("invalid argument for " + Param.vni + ": should be an integer");
        }
        if (ip != null && !IP.isIpLiteral(ip)) {
            throw new Exception("invalid argument for " + Param.via.fullname);
        }
    }

    public static void add(Command cmd) throws Exception {
        String alias = cmd.resource.alias;
        Network net = NetworkHandle.get(cmd);

        RouteTable.RouteRule rule;
        if (cmd.args.containsKey(Param.vni)) {
            int vni = Integer.parseInt(cmd.args.get(Param.vni));
            rule = new RouteTable.RouteRule(alias, net, vni);
        } else {
            IP ip = IP.from(cmd.args.get(Param.via));
            rule = new RouteTable.RouteRule(alias, net, ip);
        }

        Table tbl = VpcHandle.get(cmd.prepositionResource);
        tbl.routeTable.addRule(rule);
    }

    public static void remove(Command cmd) throws Exception {
        String alias = cmd.resource.alias;

        Table tbl = VpcHandle.get(cmd.prepositionResource);
        tbl.routeTable.delRule(alias);
    }
}
