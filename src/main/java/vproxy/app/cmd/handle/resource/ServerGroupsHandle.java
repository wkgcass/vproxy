package vproxy.app.cmd.handle.resource;

import vproxy.app.Application;
import vproxy.app.cmd.Command;
import vproxy.app.cmd.Resource;
import vproxy.app.cmd.ResourceType;
import vproxy.component.app.TcpLB;
import vproxy.component.svrgroup.ServerGroups;

import java.util.List;

public class ServerGroupsHandle {
    private ServerGroupsHandle() {
    }

    public static ServerGroups get(Resource r) throws Exception {
        return Application.get().serverGroupsHolder.get(r.alias);
    }

    public static void checkServerGroups(Resource serverGroups) throws Exception {
        if (serverGroups.parentResource != null)
            throw new Exception(serverGroups.type.fullname + " is on top level");
    }

    public static List<String> names() {
        return Application.get().serverGroupsHolder.names();
    }

    public static void add(Command cmd) throws Exception {
        Application.get().serverGroupsHolder.add(cmd.resource.alias);
    }

    public static void preCheck(Command cmd) throws Exception {
        // whether used by lb ?
        ServerGroups groups = Application.get().serverGroupsHolder.get(cmd.resource.alias);
        List<String> lbNames = Application.get().tcpLBHolder.names();
        for (String lbName : lbNames) {
            TcpLB tcpLB = Application.get().tcpLBHolder.get(lbName);
            if (tcpLB.backends.equals(groups))
                throw new Exception(ResourceType.sgs.fullname + " " + cmd.resource.alias
                    + " is used by " + ResourceType.tl.fullname + " " + tcpLB.alias);
        }
    }

    public static void forceRemove(Command cmd) throws Exception {
        Application.get().serverGroupsHolder.remove(cmd.resource.alias);
    }
}
