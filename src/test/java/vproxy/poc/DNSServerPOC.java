package vproxy.poc;

import vproxy.component.check.CheckProtocol;
import vproxy.component.check.HealthCheckConfig;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.svrgroup.Method;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.component.svrgroup.Upstream;
import vproxy.dns.DNSServer;

import java.net.InetSocketAddress;

public class DNSServerPOC {
    public static void main(String[] args) throws Exception {
        EventLoopGroup elg = new EventLoopGroup("elg0");
        ServerGroup group = new ServerGroup("example.com",
            elg, new HealthCheckConfig(0, 5000, 1, 1, CheckProtocol.none),
            Method.wrr);
        group.add("svr1", new InetSocketAddress("192.168.3.4", 80), 10);
        group.add("svr2", new InetSocketAddress("192.168.2.1", 80), 10);
        group.add("svr3", new InetSocketAddress("10.1.2.3", 80), 10);
        group.add("svr4", new InetSocketAddress("10.8.0.9", 80), 10);
        group.add("svr5", new InetSocketAddress("10.1.9.2", 80), 10);
        group.add("svr6", new InetSocketAddress("ff2e:0000:0000:abcd:0000:ffff:7f00:0001", 80), 10);
        group.add("svr7", new InetSocketAddress("ff2e:0000:0000:3456:0114:5140:7f00:0001", 80), 10);
        Upstream upstream = new Upstream("ups0");
        upstream.add(group, 10);
        DNSServer dnsServer = new DNSServer("dns0", new InetSocketAddress(53), elg, upstream, 0);
        dnsServer.start();
        elg.add("el0");
    }
}
