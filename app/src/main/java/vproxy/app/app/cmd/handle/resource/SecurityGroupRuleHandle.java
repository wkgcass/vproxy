package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Resource;
import vproxy.app.app.cmd.handle.param.NetworkHandle;
import vproxy.app.app.cmd.handle.param.PortRangeHandle;
import vproxy.app.app.cmd.handle.param.ProtocolHandle;
import vproxy.app.app.cmd.handle.param.SecGRDefaultHandle;
import vproxy.base.connection.Protocol;
import vproxy.base.util.Network;
import vproxy.base.util.Tuple;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.secure.SecurityGroupRule;

import java.util.List;
import java.util.stream.Collectors;

public class SecurityGroupRuleHandle {
    private SecurityGroupRuleHandle() {
    }

    public static List<String> names(Resource parent) throws Exception {
        SecurityGroup grp = SecurityGroupHandle.get(parent);
        return grp.getRules().stream().map(r -> r.alias).collect(Collectors.toList());
    }

    public static List<SecurityGroupRule> detail(Resource parent) throws Exception {
        SecurityGroup grp = SecurityGroupHandle.get(parent);
        return grp.getRules();
    }

    public static void remove(Command cmd) throws Exception {
        SecurityGroup grp = SecurityGroupHandle.get(cmd.prepositionResource);
        grp.removeRule(cmd.resource.alias);
    }

    public static void add(Command cmd) throws Exception {
        SecurityGroup grp = SecurityGroupHandle.get(cmd.prepositionResource);

        Network net = NetworkHandle.get(cmd);
        Protocol protocol = ProtocolHandle.get(cmd);
        Tuple<Integer, Integer> range = PortRangeHandle.get(cmd);
        boolean allow = SecGRDefaultHandle.get(cmd);

        SecurityGroupRule rule = new SecurityGroupRule(
            cmd.resource.alias, net,
            protocol, range.left, range.right,
            allow
        );

        grp.addRule(rule);
    }
}
