package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Resource;
import vproxy.app.app.cmd.ResourceType;
import vproxy.base.component.elgroup.EventLoopGroup;
import vproxy.base.component.svrgroup.ServerGroup;
import vproxy.base.util.exception.XException;
import vproxy.component.app.Socks5Server;
import vproxy.component.app.TcpLB;
import vproxy.dns.DNSServer;
import vproxy.vswitch.Switch;

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

    public static void add(Command cmd) throws Exception {
        Application.get().eventLoopGroupHolder.add(cmd.resource.alias);
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
