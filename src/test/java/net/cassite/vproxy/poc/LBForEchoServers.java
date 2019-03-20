package net.cassite.vproxy.poc;

import net.cassite.vproxy.component.app.TcpLB;
import net.cassite.vproxy.component.check.HealthCheckConfig;
import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.ClosedException;
import net.cassite.vproxy.component.secure.SecurityGroup;
import net.cassite.vproxy.component.svrgroup.Method;
import net.cassite.vproxy.component.svrgroup.ServerGroup;
import net.cassite.vproxy.component.svrgroup.ServerGroups;
import net.cassite.vproxy.selector.SelectorEventLoop;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;

@SuppressWarnings("Duplicates")
public class LBForEchoServers {
    private static volatile boolean doContinue = true;

    public static void main(String[] args) throws AlreadyExistException, IOException, ClosedException, InterruptedException {
        EventLoopGroup eventLoopGroup = new EventLoopGroup("eventLoopGroup");
        ServerGroups serverGroups = new ServerGroups("serverGroups");
        ServerGroup grp1 = new ServerGroup("grp1", eventLoopGroup,
            new HealthCheckConfig(200, 800, 4, 5),
            Method.wrr);
        ServerGroup grp2 = new ServerGroup("grp2", eventLoopGroup,
            new HealthCheckConfig(200, 800, 4, 5),
            Method.wrr);
        serverGroups.add(grp1, 10);
        serverGroups.add(grp2, 10);
        TcpLB lb = new TcpLB("myLb",
            eventLoopGroup, eventLoopGroup, // use the same group for acceptor and worker
            new InetSocketAddress(18080), serverGroups,
            8, 4, // make buffers small to demonstrate what happen when buffer is full
            SecurityGroup.allowAll(),
            0
        );
        lb.start();
        // add each group one server
        grp1.add("s1", new InetSocketAddress("127.0.0.1", 19080), 10);
        grp2.add("s2", new InetSocketAddress("127.0.0.1", 19081), 10);

        // print
        SelectorEventLoop delayPrintLoop = SelectorEventLoop.open();
        runTimer(delayPrintLoop, lb, grp1, grp2);
        new Thread(delayPrintLoop::loop).start();

        // start client in another thread
        new Thread(() -> {
            try {
                AlphabetBlockingClient.runBlock(18080, 60, true);
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
        System.out.println("\033[1;30m--------------------stop echo1 in 10 seconds---------------\033[0m");
        Thread.sleep(10000);
        System.out.println("\033[1;30m-------------------------stop echo1 now--------------------\033[0m");
        echo1.close();

        while (doContinue) {
            Thread.sleep(1);
        }
        System.out.println("\033[1;30m------------------------stop all servers-------------------\033[0m");
        // close the event loop group
        // which means stop all event loops inside the group
        eventLoopGroup.close();
        // close echo2
        echo2.close();
        // close print loop
        delayPrintLoop.close();
    }

    private static void runTimer(SelectorEventLoop eventLoop, TcpLB lb, ServerGroup grp1, ServerGroup grp2) {
        eventLoop.delay(1000, () -> {
            long serverTo = lb.server.getToRemoteBytes();
            long serverFrom = lb.server.getFromRemoteBytes();
            long accepted = lb.server.getHistoryAcceptedConnectionCount();
            System.out.println("server:\t\twrite to remote = \033[0;36m" + serverTo + "\033[0m\tread from remote: \033[0;36m" + serverFrom + "\033[0m\taccepted: \033[0;36m" + accepted + "\033[0m");

            for (ServerGroup g : Arrays.asList(grp1, grp2)) {
                for (ServerGroup.ServerHandle h : g.getServerHandles()) {
                    long hTo = h.getToRemoteBytes();
                    long hFrom = h.getFromRemoteBytes();
                    System.out.println(g.alias + "" + "." + h.alias + ":\twrite to remote = \033[0;36m" + hTo + "\033[0m\tread from remote: \033[0;36m" + hFrom + "\033[0m");
                }
            }
            runTimer(eventLoop, lb, grp1, grp2);
        });
    }
}
