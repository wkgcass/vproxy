package io.vproxy.poc;

import io.vproxy.base.component.check.CheckProtocol;
import io.vproxy.base.component.check.HealthCheckConfig;
import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.component.svrgroup.Method;
import io.vproxy.base.component.svrgroup.ServerGroup;
import io.vproxy.base.util.AnnotationKeys;
import io.vproxy.base.util.Annotations;
import io.vproxy.component.secure.SecurityGroup;
import io.vproxy.component.svrgroup.Upstream;
import io.vproxy.dns.DNSServer;
import io.vproxy.vfd.IPPort;

import java.util.Map;

public class DNSServerPOC {
    public static void main(String[] args) throws Exception {
        EventLoopGroup elg = new EventLoopGroup("elg0");
        ServerGroup group = new ServerGroup("sg0",
            elg, new HealthCheckConfig(0, 5000, 1, 1, CheckProtocol.none),
            Method.wrr);
        group.setAnnotations(new Annotations(Map.of(AnnotationKeys.ServerGroup_HintHost.name, "example.com")));
        group.add("svr1", new IPPort("192.168.3.4", 80), 10);
        group.add("svr2", new IPPort("192.168.2.1", 80), 10);
        group.add("svr3", new IPPort("10.1.2.3", 80), 10);
        group.add("svr4", new IPPort("10.8.0.9", 80), 10);
        group.add("svr5", new IPPort("10.1.9.2", 80), 10);
        group.add("svr6", new IPPort("ff2e:0000:0000:abcd:0000:ffff:7f00:0001", 80), 10);
        group.add("svr7", new IPPort("ff2e:0000:0000:3456:0114:5140:7f00:0001", 80), 10);
        Upstream upstream = new Upstream("ups0");
        upstream.add(group, 10);
        DNSServer dnsServer = new DNSServer("dns0", new IPPort(553), elg, upstream, 0, SecurityGroup.allowAll());
        dnsServer.start();
        elg.add("el0");
    }
}
