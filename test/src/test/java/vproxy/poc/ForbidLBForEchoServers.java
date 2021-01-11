package vproxy.poc;

import vfd.IP;
import vfd.IPPort;
import vproxy.component.app.TcpLB;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.secure.SecurityGroupRule;
import vproxy.component.svrgroup.Upstream;
import vproxybase.Config;
import vproxybase.component.check.HealthCheckConfig;
import vproxybase.component.elgroup.EventLoopGroup;
import vproxybase.component.svrgroup.Method;
import vproxybase.component.svrgroup.ServerGroup;
import vproxybase.connection.Protocol;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.util.Network;
import vproxybase.util.thread.VProxyThread;
import vproxybase.util.exception.AlreadyExistException;
import vproxybase.util.exception.ClosedException;
import vproxybase.util.exception.NotFoundException;

import java.io.IOException;

@SuppressWarnings("Duplicates")
public class ForbidLBForEchoServers {
    private static volatile boolean doContinue = true;

    public static void main(String[] args) throws AlreadyExistException, IOException, ClosedException, InterruptedException, NotFoundException {
        EventLoopGroup eventLoopGroup = new EventLoopGroup("eventLoopGroup");
        Upstream upstream = new Upstream("upstream");
        ServerGroup grp1 = new ServerGroup("grp1", eventLoopGroup,
            new HealthCheckConfig(200, 800, 1, 5),
            Method.wrr);
        ServerGroup grp2 = new ServerGroup("grp2", eventLoopGroup,
            new HealthCheckConfig(200, 800, 1, 5),
            Method.wrr);
        upstream.add(grp1, 10);
        upstream.add(grp2, 10);
        SecurityGroup secg = new SecurityGroup("secg0", false);
        TcpLB lb = new TcpLB("myLb",
            eventLoopGroup, eventLoopGroup, // use the same group for acceptor and worker
            new IPPort(18080), upstream,
            Config.tcpTimeout, 8, 4, // make buffers small to demonstrate what happen when buffer is full
            secg
        );
        lb.start();
        // add each group one server
        grp1.add("s1", new IPPort("127.0.0.1", 19080), 10);
        grp2.add("s2", new IPPort("127.0.0.1", 19081), 10);

        // start client in another thread
        VProxyThread.create(() -> {
            try {
                AlphabetBlockingClient.runBlock(18080, 40, true);
            } catch (Exception e) {
                e.printStackTrace();
            }
            doContinue = false;
        }, "ClientThread1").start();

        // create 2 event loops
        eventLoopGroup.add("el1");
        eventLoopGroup.add("el2");

        // create echo server on 19080/19081
        SelectorEventLoop echo1 = SelectorEventLoopEchoServer.createServer(19080);
        SelectorEventLoop echo2 = SelectorEventLoopEchoServer.createServer(19081);

        VProxyThread.create(echo1::loop, "echo1").start();
        VProxyThread.create(echo2::loop, "echo2").start();

        System.out.println("\033[1;30m--------------------all servers started now----------------\033[0m");
        Thread.sleep(10000);
        System.out.println("\033[1;30m---------------------allow port 18080 now------------------\033[0m");
        SecurityGroupRule rule = new SecurityGroupRule("allow18080",
            new Network(IP.blockParseAddress("127.0.0.1"), Network.parseMask(32)),
            Protocol.TCP, 18080, 18080, true);
        secg.addRule(rule);
        Thread.sleep(10000);
        System.out.println("\033[1;30m--------------------forbid port 18080 now------------------\033[0m");
        secg.removeRule("allow18080");
        Thread.sleep(10000);
        System.out.println("\033[1;30m---------------------allow port 18080 now------------------\033[0m");
        secg.addRule(rule);

        while (doContinue) {
            Thread.sleep(1);
        }
        System.out.println("\033[1;30m------------------------stop all servers-------------------\033[0m");
        // close the event loop group
        // which means stop all event loops inside the group
        eventLoopGroup.close();
        // close echo servers
        echo1.close();
        echo2.close();
    }
}
