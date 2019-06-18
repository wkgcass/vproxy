package vproxy.app.cmd.handle.resource;

import vproxy.app.Application;
import vproxy.app.cmd.Command;
import vproxy.app.cmd.Resource;
import vproxy.app.cmd.ResourceType;
import vproxy.component.app.Socks5Server;
import vproxy.component.app.TcpLB;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.svrgroup.ServerGroup;

import java.util.List;

public class EventLoopGroupHandle {
    private EventLoopGroupHandle() {
    }

    public static void checkEventLoopGroup(Resource eventLoopGroup) throws Exception {
        if (eventLoopGroup.parentResource != null)
            throw new Exception(eventLoopGroup.type.fullname + " is on top level");
    }

    public static EventLoopGroup get(Resource resource) throws Exception {
        return get(resource.alias);
    }

    public static EventLoopGroup get(String resource) throws Exception {
        return Application.get().eventLoopGroupHolder.get(resource);
    }

    public static List<String> names() {
        return Application.get().eventLoopGroupHolder.names();
    }

    public static void add(Command cmd) throws Exception {
        Application.get().eventLoopGroupHolder.add(cmd.resource.alias);
    }

    public static void preCheck(Command cmd) throws Exception {
        String toRemoveName = cmd.resource.alias;
        EventLoopGroup g = Application.get().eventLoopGroupHolder.get(toRemoveName);

        // check default
        if (Application.isDefaultEventLoopGroupName(toRemoveName)) {
            throw new Exception("cannot remove a default event loop " + toRemoveName);
        }
        // check tcp lb
        for (String name : Application.get().tcpLBHolder.names()) {
            TcpLB tcpLB = Application.get().tcpLBHolder.get(name);
            if (tcpLB.acceptorGroup.equals(g) || tcpLB.workerGroup.equals(g))
                throw new Exception(ResourceType.elg.fullname + " " + toRemoveName + " is used by " + ResourceType.tl.fullname + " " + tcpLB.alias);
        }
        // check socks5
        for (String name : Application.get().socks5ServerHolder.names()) {
            Socks5Server socks5 = Application.get().socks5ServerHolder.get(name);
            if (socks5.acceptorGroup.equals(g) || socks5.workerGroup.equals(g))
                throw new Exception(ResourceType.elg.fullname + " " + toRemoveName + " is used by " + ResourceType.socks5.fullname + " " + socks5.alias);
        }
        // check servers group
        for (String name : Application.get().serverGroupHolder.names()) {
            ServerGroup sg = Application.get().serverGroupHolder.get(name);
            if (sg.eventLoopGroup.equals(g))
                throw new Exception(ResourceType.elg.fullname + " " + toRemoveName + " is used by " + ResourceType.sg.fullname + " " + sg.alias);
        }
    }

    public static void forceRemvoe(Command cmd) throws Exception {
        String toRemoveName = cmd.resource.alias;
        Application.get().eventLoopGroupHolder.removeAndClose(toRemoveName);
    }
}
