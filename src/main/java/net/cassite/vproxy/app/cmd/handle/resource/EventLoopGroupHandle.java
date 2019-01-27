package net.cassite.vproxy.app.cmd.handle.resource;

import net.cassite.vproxy.app.Application;
import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.app.cmd.Resource;
import net.cassite.vproxy.app.cmd.ResourceType;
import net.cassite.vproxy.component.app.TcpLB;
import net.cassite.vproxy.component.elgroup.EventLoopGroup;

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

        // check tcp lb
        for (String name : Application.get().tcpLBHolder.names()) {
            TcpLB tcpLB = Application.get().tcpLBHolder.get(name);
            if (tcpLB.acceptorGroup.equals(g) || tcpLB.workerGroup.equals(g))
                throw new Exception(ResourceType.elg.fullname + " " + toRemoveName + " is used by " + ResourceType.tl.fullname + " " + tcpLB.alias);
        }
    }

    public static void forceRemvoe(Command cmd) throws Exception {
        String toRemoveName = cmd.resource.alias;
        Application.get().eventLoopGroupHolder.removeAndClose(toRemoveName);
    }

    public static class EventLoopGroupRef {
        public final EventLoopGroup elg;

        public EventLoopGroupRef(EventLoopGroup elg) {
            this.elg = elg;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(elg.alias);
            for (String name : elg.names()) {
                sb.append("\n    ").append(name);
            }
            return sb.toString();
        }
    }
}
