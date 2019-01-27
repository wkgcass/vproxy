package net.cassite.vproxy.app.cmd.handle.resource;

import net.cassite.vproxy.app.Application;
import net.cassite.vproxy.app.SecurityGroupHolder;
import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.app.cmd.Param;
import net.cassite.vproxy.app.cmd.Resource;
import net.cassite.vproxy.app.cmd.ResourceType;
import net.cassite.vproxy.app.cmd.handle.param.SecGRDefaultHandle;
import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.component.secure.SecurityGroup;

import java.util.ArrayList;
import java.util.List;

public class SecurityGroupHandle {
    private SecurityGroupHandle() {
    }

    public static SecurityGroup get(String alias) throws Exception {
        return Application.get().securityGroupHolder.get(alias);
    }

    public static SecurityGroup get(Resource resource) throws Exception {
        return get(resource.alias);
    }

    public static void checkCreateSecurityGroup(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.secgrdefault))
            throw new Exception("missing argument " + Param.secgrdefault.fullname);
        SecGRDefaultHandle.check(cmd);
    }

    public static List<String> names() {
        return Application.get().securityGroupHolder.names();
    }

    public static List<SecurityGroup> detail() throws NotFoundException {
        SecurityGroupHolder h = Application.get().securityGroupHolder;
        List<String> names = h.names();
        List<SecurityGroup> secgList = new ArrayList<>(names.size());
        for (String name : names) {
            secgList.add(h.get(name));
        }
        return secgList;
    }

    public static void add(Command cmd) throws AlreadyExistException {
        String alias = cmd.resource.alias;
        boolean defaultAllow = SecGRDefaultHandle.get(cmd);
        Application.get().securityGroupHolder.add(alias, defaultAllow);
    }

    public static void preCheck(Command cmd) throws Exception {
        for (TcpLBHandle.TcpLBRef ref : TcpLBHandle.details()) {
            if (ref.tcpLB.securityGroup.alias.equals(cmd.resource.alias)) {
                throw new Exception(ResourceType.secg.fullname + " " + cmd.resource.alias + " is used by " + ResourceType.tl.fullname + " " + ref.tcpLB.alias);
            }
        }
    }

    public static void forceRemove(Command cmd) throws NotFoundException {
        Application.get().securityGroupHolder.remove(cmd.resource.alias);
    }

    public static void checkSecurityGroup(Resource secg) throws Exception {
        if (secg.parentResource != null)
            throw new Exception(secg.type.fullname + " is on top level");
    }
}
