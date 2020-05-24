package vproxyapp.app.cmd.handle.resource;

import vproxy.component.secure.SecurityGroup;
import vproxyapp.app.Application;
import vproxyapp.app.SecurityGroupHolder;
import vproxyapp.app.cmd.Command;
import vproxyapp.app.cmd.Param;
import vproxyapp.app.cmd.Resource;
import vproxyapp.app.cmd.ResourceType;
import vproxyapp.app.cmd.handle.param.SecGRDefaultHandle;
import vproxybase.util.exception.AlreadyExistException;
import vproxybase.util.exception.NotFoundException;
import vproxybase.util.exception.XException;

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

    public static void update(Command cmd) throws NotFoundException {
        String alias = cmd.resource.alias;
        SecurityGroup secg = Application.get().securityGroupHolder.get(alias);
        if (cmd.args.containsKey(Param.secgrdefault)) {
            secg.defaultAllow = SecGRDefaultHandle.get(cmd);
        }
    }

    public static void preCheck(Command cmd) throws Exception {
        for (TcpLBHandle.TcpLBRef ref : TcpLBHandle.details()) {
            if (ref.tcpLB.securityGroup.alias.equals(cmd.resource.alias)) {
                throw new XException(ResourceType.secg.fullname + " " + cmd.resource.alias + " is used by " + ResourceType.tl.fullname + " " + ref.tcpLB.alias);
            }
        }
        for (var ref : Socks5ServerHandle.details()) {
            if (ref.socks5.securityGroup.alias.equals(cmd.resource.alias)) {
                throw new XException(ResourceType.secg.fullname + " " + cmd.resource.alias + " is used by " + ResourceType.socks5.fullname + " " + ref.socks5.alias);
            }
        }
        for (var ref : DNSServerHandle.details()) {
            if (ref.dnsServer.securityGroup.alias.equals(cmd.resource.alias)) {
                throw new XException(ResourceType.secg.fullname + " " + cmd.resource.alias + " is used by " + ResourceType.dns.fullname + " " + ref.dnsServer.alias);
            }
        }
        for (var ref : SwitchHandle.details()) {
            if (ref.sw.bareVXLanAccess.alias.equals(cmd.resource.alias)) {
                throw new XException(ResourceType.secg.fullname + " " + cmd.resource.alias + " is used by " + ResourceType.sw.fullname + " " + ref.sw.alias);
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
