package vproxy.app.cmd.handle.resource;

import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;
import vproxy.app.cmd.Resource;
import vproxy.app.cmd.handle.param.NetworkHandle;
import vproxy.app.cmd.handle.param.PortRangeHandle;
import vproxy.app.cmd.handle.param.ProtocolHandle;
import vproxy.app.cmd.handle.param.SecGRDefaultHandle;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.secure.SecurityGroupRule;
import vproxy.connection.Protocol;
import vproxy.util.Tuple;

import java.util.List;
import java.util.stream.Collectors;

public class SecurityGroupRuleHandle {
    private SecurityGroupRuleHandle() {
    }

    public static void checkCreateSecurityGroupRule(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.net))
            throw new Exception("missing argument " + Param.net.fullname);
        if (!cmd.args.containsKey(Param.protocol))
            throw new Exception("missing argument " + Param.protocol.fullname);
        if (!cmd.args.containsKey(Param.portrange))
            throw new Exception("missing argument " + Param.portrange.fullname);
        if (!cmd.args.containsKey(Param.secgrdefault))
            throw new Exception("missing argument " + Param.secgrdefault.fullname);

        NetworkHandle.check(cmd);
        ProtocolHandle.check(cmd);
        PortRangeHandle.check(cmd);
        SecGRDefaultHandle.check(cmd);
    }

    public static List<String> names(Resource parent) throws Exception {
        SecurityGroup grp = SecurityGroupHandle.get(parent);
        return grp.getRules().stream().map(r -> r.alias).collect(Collectors.toList());
    }

    public static List<SecurityGroupRule> detail(Resource parent) throws Exception {
        SecurityGroup grp = SecurityGroupHandle.get(parent);
        return grp.getRules();
    }

    public static void forceRemove(Command cmd) throws Exception {
        SecurityGroup grp = SecurityGroupHandle.get(cmd.prepositionResource);
        grp.removeRule(cmd.resource.alias);
    }

    public static void add(Command cmd) throws Exception {
        SecurityGroup grp = SecurityGroupHandle.get(cmd.prepositionResource);

        Tuple<byte[], byte[]> net = NetworkHandle.get(cmd);
        Protocol protocol = ProtocolHandle.get(cmd);
        Tuple<Integer, Integer> range = PortRangeHandle.get(cmd);
        boolean allow = SecGRDefaultHandle.get(cmd);

        SecurityGroupRule rule = new SecurityGroupRule(
            cmd.resource.alias, net.left, net.right,
            protocol, range.left, range.right,
            allow
        );

        grp.addRule(rule);
    }
}
