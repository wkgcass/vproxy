package net.cassite.vproxy.example;

import net.cassite.vproxy.component.app.TcpLB;
import net.cassite.vproxy.component.check.HealthCheckConfig;
import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.ClosedException;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.component.secure.SecurityGroup;
import net.cassite.vproxy.component.secure.SecurityGroupRule;
import net.cassite.vproxy.component.svrgroup.Method;
import net.cassite.vproxy.component.svrgroup.ServerGroup;
import net.cassite.vproxy.component.svrgroup.ServerGroups;
import net.cassite.vproxy.connection.Protocol;
import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.util.Utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

@SuppressWarnings("Duplicates")
public class ForbidLBForEchoServers {
    private static volatile boolean doContinue = true;

    public static void main(String[] args) throws AlreadyExistException, IOException, ClosedException, InterruptedException, NotFoundException {
        EventLoopGroup eventLoopGroup = new EventLoopGroup("eventLoopGroup");
        ServerGroups serverGroups = new ServerGroups("serverGroups");
        ServerGroup grp1 = new ServerGroup("grp1", eventLoopGroup,
            new HealthCheckConfig(200, 800, 1, 5),
            Method.wrr);
        ServerGroup grp2 = new ServerGroup("grp2", eventLoopGroup,
            new HealthCheckConfig(200, 800, 1, 5),
            Method.wrr);
        serverGroups.add(grp1);
        serverGroups.add(grp2);
        SecurityGroup secg = new SecurityGroup("secg0", false);
        TcpLB lb = new TcpLB("myLb",
            eventLoopGroup, eventLoopGroup, // use the same group for acceptor and worker
            new InetSocketAddress(18080), serverGroups,
            8, 4, // make buffers small to demonstrate what happen when buffer is full
            secg
        );
        lb.start();
        // add each group one server
        grp1.add("s1", new InetSocketAddress("127.0.0.1", 19080), InetAddress.getByName("127.0.0.1"), 10);
        grp2.add("s2", new InetSocketAddress("127.0.0.1", 19081), InetAddress.getByName("127.0.0.1"), 10);

        // start client in another thread
        new Thread(() -> {
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

        new Thread(echo1::loop, "echo1").start();
        new Thread(echo2::loop, "echo2").start();

        System.out.println("\033[1;30m--------------------all servers started now----------------\033[0m");
        Thread.sleep(10000);
        System.out.println("\033[1;30m---------------------allow port 18080 now------------------\033[0m");
        SecurityGroupRule rule = new SecurityGroupRule("allow18080",
            Utils.parseAddress("127.0.0.1"), Utils.parseMask(32),
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
