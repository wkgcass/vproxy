package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.app.app.cmd.Resource;
import io.vproxy.app.app.cmd.handle.param.NetworkHandle;
import io.vproxy.base.util.Network;
import io.vproxy.base.util.Utils;
import io.vproxy.vfd.IP;
import io.vproxy.vswitch.RouteTable;
import io.vproxy.vswitch.VirtualNetwork;

import java.util.List;
import java.util.stream.Collectors;

public class RouteHandle {
    private RouteHandle() {
    }

    public static List<String> names(Resource parent) throws Exception {
        VirtualNetwork net = VrfHandle.get(parent);
        return net.routeTable.getRules().stream().map(r -> r.alias).collect(Collectors.toList());
    }

    public static List<RouteTable.RouteRule> list(Resource parent) throws Exception {
        VirtualNetwork net = VrfHandle.get(parent);
        return net.routeTable.getRules();
    }

    public static void checkCreateRoute(Command cmd) throws Exception {
        String net = cmd.args.get(Param.net);
        if (net == null) {
            throw new Exception("missing " + Param.net.fullname);
        }
        NetworkHandle.check(cmd);
        String vrf = cmd.args.get(Param.vrf);
        String ip = cmd.args.get(Param.via);
        if (vrf == null && ip == null) {
            throw new Exception("missing " + Param.vrf.fullname + " or " + Param.via.fullname);
        }
        if (vrf != null && ip != null) {
            throw new Exception("cannot specify " + Param.vrf.fullname + " and " + Param.via.fullname + " at the same time");
        }
        if (vrf != null && !Utils.isInteger(vrf)) {
            throw new Exception("invalid argument for " + Param.vrf + ": should be an integer");
        }
        if (ip != null && !IP.isIpLiteral(ip)) {
            throw new Exception("invalid argument for " + Param.via.fullname);
        }
    }

    public static void add(Command cmd) throws Exception {
        String alias = cmd.resource.alias;
        Network net = NetworkHandle.get(cmd);

        RouteTable.RouteRule rule;
        if (cmd.args.containsKey(Param.vrf)) {
            int vrf = Integer.parseInt(cmd.args.get(Param.vrf));
            rule = new RouteTable.RouteRule(alias, net, vrf);
        } else {
            IP ip = IP.from(cmd.args.get(Param.via));
            rule = new RouteTable.RouteRule(alias, net, ip);
        }

        VirtualNetwork vnet = VrfHandle.get(cmd.prepositionResource);
        vnet.routeTable.addRule(rule);
    }

    public static void remove(Command cmd) throws Exception {
        String alias = cmd.resource.alias;

        VirtualNetwork net = VrfHandle.get(cmd.prepositionResource);
        net.routeTable.delRule(alias);
    }
}
