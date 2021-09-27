package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.app.app.cmd.Resource;
import io.vproxy.app.app.cmd.ResourceType;
import io.vproxy.app.app.cmd.handle.param.AnnotationsHandle;
import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.component.svrgroup.ServerGroup;
import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.exception.XException;
import io.vproxy.component.app.Socks5Server;
import io.vproxy.component.app.TcpLB;
import io.vproxy.dns.DNSServer;
import io.vproxy.vswitch.Switch;

import java.util.ArrayList;
import java.util.List;

public class EventLoopGroupHandle {
    private EventLoopGroupHandle() {
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

    public static List<EventLoopGroup> details() throws Exception {
        var names = Application.get().eventLoopGroupHolder.names();
        var ls = new ArrayList<EventLoopGroup>(names.size());
        for (String name : names) {
            ls.add(Application.get().eventLoopGroupHolder.get(name));
        }
        return ls;
    }

    public static void add(Command cmd) throws Exception {
        Annotations anno;
        if (cmd.args.containsKey(Param.anno)) {
            anno = AnnotationsHandle.get(cmd);
        } else {
            anno = new Annotations();
        }
        Application.get().eventLoopGroupHolder.add(cmd.resource.alias, anno);
    }

    public static void preRemoveCheck(Command cmd) throws Exception {
        String toRemoveName = cmd.resource.alias;
        EventLoopGroup g = Application.get().eventLoopGroupHolder.get(toRemoveName);

        // check default
        if (Application.isDefaultEventLoopGroupName(toRemoveName)) {
            throw new XException("cannot remove a default event loop " + toRemoveName);
        }
        // check tcp lb
        for (String name : Application.get().tcpLBHolder.names()) {
            TcpLB tcpLB = Application.get().tcpLBHolder.get(name);
            if (tcpLB.acceptorGroup.equals(g) || tcpLB.workerGroup.equals(g))
                throw new XException(ResourceType.elg.fullname + " " + toRemoveName + " is used by " + ResourceType.tl.fullname + " " + tcpLB.alias);
        }
        // check socks5
        for (String name : Application.get().socks5ServerHolder.names()) {
            Socks5Server socks5 = Application.get().socks5ServerHolder.get(name);
            if (socks5.acceptorGroup.equals(g) || socks5.workerGroup.equals(g))
                throw new XException(ResourceType.elg.fullname + " " + toRemoveName + " is used by " + ResourceType.socks5.fullname + " " + socks5.alias);
        }
        // check dns
        for (String name : Application.get().dnsServerHolder.names()) {
            DNSServer dns = Application.get().dnsServerHolder.get(name);
            if (dns.eventLoopGroup.equals(g)) {
                throw new XException(ResourceType.elg.fullname + " " + toRemoveName + " is used by " + ResourceType.dns.fullname + " " + dns.alias);
            }
        }
        // check servers group
        for (String name : Application.get().serverGroupHolder.names()) {
            ServerGroup sg = Application.get().serverGroupHolder.get(name);
            if (sg.eventLoopGroup.equals(g)) {
                throw new XException(ResourceType.elg.fullname + " " + toRemoveName + " is used by " + ResourceType.sg.fullname + " " + sg.alias);
            }
        }
        // check switch
        for (String name : Application.get().switchHolder.names()) {
            Switch sw = Application.get().switchHolder.get(name);
            if (sw.eventLoopGroup.equals(g)) {
                throw new XException(ResourceType.elg.fullname + " " + toRemoveName + " is used by " + ResourceType.sw.fullname + " " + sw.alias);
            }
        }
    }

    public static void remvoe(Command cmd) throws Exception {
        String toRemoveName = cmd.resource.alias;
        Application.get().eventLoopGroupHolder.removeAndClose(toRemoveName);
    }
}
