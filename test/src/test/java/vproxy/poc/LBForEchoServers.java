package vproxy.poc;

import vfd.IPPort;
import vproxy.component.app.TcpLB;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.svrgroup.Upstream;
import vproxybase.Config;
import vproxybase.component.check.HealthCheckConfig;
import vproxybase.component.elgroup.EventLoopGroup;
import vproxybase.component.svrgroup.Method;
import vproxybase.component.svrgroup.ServerGroup;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.util.thread.VProxyThread;
import vproxybase.util.exception.AlreadyExistException;
import vproxybase.util.exception.ClosedException;

import java.io.IOException;
import java.util.Arrays;

@SuppressWarnings("Duplicates")
public class LBForEchoServers {
    private static volatile boolean doContinue = true;

    public static void main(String[] args) throws AlreadyExistException, IOException, ClosedException, InterruptedException {
        EventLoopGroup acceptorGroup = new EventLoopGroup("acceptorGroup");
        acceptorGroup.add("acceptor");
        EventLoopGroup eventLoopGroup = new EventLoopGroup("eventLoopGroup");
        Upstream upstream = new Upstream("upstream");
        ServerGroup grp1 = new ServerGroup("grp1", eventLoopGroup,
            new HealthCheckConfig(200, 800, 4, 5),
            Method.wrr);
        ServerGroup grp2 = new ServerGroup("grp2", eventLoopGroup,
            new HealthCheckConfig(200, 800, 4, 5),
            Method.wrr);
        upstream.add(grp1, 10);
        upstream.add(grp2, 10);
        TcpLB lb = new TcpLB("myLb",
            acceptorGroup, eventLoopGroup, // use the same group for acceptor and worker
            new IPPort(18080), upstream,
            Config.tcpTimeout, 8, 4, // make buffers small to demonstrate what happen when buffer is full
            SecurityGroup.allowAll()
        );
        lb.start();
        // add each group one server
        grp1.add("s1", new IPPort("127.0.0.1", 19080), 10);
        grp2.add("s2", new IPPort("127.0.0.1", 19081), 10);

        // print
        SelectorEventLoop delayPrintLoop = SelectorEventLoop.open();
        runTimer(delayPrintLoop, lb, grp1, grp2);
        VProxyThread.create(delayPrintLoop::loop, "delayPrintLoop").start();

        // start client in another thread
        VProxyThread.create(() -> {
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

        VProxyThread.create(echo1::loop, "echo1").start();
        VProxyThread.create(echo2::loop, "echo2").start();

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
        acceptorGroup.close();
        eventLoopGroup.close();
        // close echo2
        echo2.close();
        // close print loop
        delayPrintLoop.close();
    }

    private static void runTimer(SelectorEventLoop eventLoop, TcpLB lb, ServerGroup grp1, ServerGroup grp2) {
        eventLoop.delay(1000, () -> {
            long serverTo = lb.servers.keySet().stream().findFirst().get().getToRemoteBytes();
            long serverFrom = lb.servers.keySet().stream().findFirst().get().getFromRemoteBytes();
            long accepted = lb.servers.keySet().stream().findFirst().get().getHistoryAcceptedConnectionCount();
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
