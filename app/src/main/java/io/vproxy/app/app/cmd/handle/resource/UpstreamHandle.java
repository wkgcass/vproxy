package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Resource;
import io.vproxy.app.app.cmd.ResourceType;
import io.vproxy.base.util.exception.XException;
import io.vproxy.component.app.Socks5Server;
import io.vproxy.component.app.TcpLB;
import io.vproxy.component.svrgroup.Upstream;
import io.vproxy.dns.DNSServer;

import java.util.List;

public class UpstreamHandle {
    private UpstreamHandle() {
    }

    public static Upstream get(Resource r) throws Exception {
        return Application.get().upstreamHolder.get(r.alias);
    }

    public static List<String> names() {
        return Application.get().upstreamHolder.names();
    }

    public static void add(Command cmd) throws Exception {
        Application.get().upstreamHolder.add(cmd.resource.alias);
    }

    public static void preRemoveCheck(Command cmd) throws Exception {
        // check tcp lb
        Upstream ups = Application.get().upstreamHolder.get(cmd.resource.alias);
        for (String name : Application.get().tcpLBHolder.names()) {
            TcpLB tcpLB = Application.get().tcpLBHolder.get(name);
            if (tcpLB.backend.equals(ups))
                throw new XException(ResourceType.ups.fullname + " " + cmd.resource.alias
                    + " is used by " + ResourceType.tl.fullname + " " + tcpLB.alias);
        }
        // check socks5
        for (String name : Application.get().socks5ServerHolder.names()) {
            Socks5Server socks5 = Application.get().socks5ServerHolder.get(name);
            if (socks5.backend.equals(ups))
                throw new XException(ResourceType.ups.fullname + " " + cmd.resource.alias
                    + " is used by " + ResourceType.socks5.fullname + " " + socks5.alias);
        }
        // check dns
        for (String name : Application.get().dnsServerHolder.names()) {
            DNSServer dns = Application.get().dnsServerHolder.get(name);
            if (dns.rrsets.equals(ups))
                throw new XException(ResourceType.ups.fullname + " " + cmd.resource.alias
                    + " is used by " + ResourceType.dns.fullname + " " + dns.alias);
        }
    }

    public static void remove(Command cmd) throws Exception {
        Application.get().upstreamHolder.remove(cmd.resource.alias);
    }
}
