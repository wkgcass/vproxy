package net.cassite.vproxy.example;

import net.cassite.vproxy.component.check.HealthCheckConfig;
import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.exception.ClosedException;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.component.svrgroup.ServerGroup;
import net.cassite.vproxy.selector.SelectorEventLoop;

import java.io.IOException;
import java.net.InetSocketAddress;

public class ServerGroupExample {
    public static void main(String[] args) throws AlreadyExistException, IOException, ClosedException, InterruptedException, NotFoundException {
        int portA = 18080;
        int portB = 18081;

        EventLoopGroup eventLoopGroup = new EventLoopGroup();
        ServerGroup serverGroup = new ServerGroup("server group", eventLoopGroup,
            new HealthCheckConfig(200, 800, 4, 5));
        serverGroup.add(new InetSocketAddress("127.0.0.1", portA));
        serverGroup.add(new InetSocketAddress("127.0.0.1", portB));

        // create a event loop only for checking the active server
        SelectorEventLoop eventLoop = SelectorEventLoop.open();
        runTimer(eventLoop, serverGroup);
        new Thread(eventLoop::loop).start();

        eventLoopGroup.add("my loop 1"); // re-dispatch to all threads (currently 1 thread ),
        // cursor = 1 use = 0
        eventLoopGroup.add("my loop 2"); // re-dispatch to all threads (currently 2 threads),
        // for serverA cursor = 2 use = 1, for serverB cursor = 1 use = 0

        System.out.println("\033[1;30m--------------------------------------------------------------------------------------------------server group created---------------\033[0m");

        Thread.sleep(5000);
        System.out.println("\033[1;30m--------------------------------------------------------------------------------------------create serverA on " + portA + "----------\033[0m");
        SelectorEventLoop serverA = SelectorEventLoopEchoServer.createServer(portA);
        new Thread(serverA::loop, "serverA Event Loop Thread").start();

        Thread.sleep(5000);
        System.out.println("\033[1;30m--------------------------------------------------------------------------------------------create serverB on " + portB + "----------\033[0m");
        SelectorEventLoop serverB = SelectorEventLoopEchoServer.createServer(portB);
        new Thread(serverB::loop, "serverB Event Loop Thread").start();

        Thread.sleep(5000);
        System.out.println("\033[1;30m---------------------------------------------------------------------------------------------let's remove serverA from group---------\033[0m");
        serverGroup.remove(new InetSocketAddress("127.0.0.1", portA));

        Thread.sleep(5000);
        System.out.println("\033[1;30m-------------------------------------------------------------------------------------------------let's add serverA back--------------\033[0m");
        serverGroup.add(new InetSocketAddress("127.0.0.1", portA)); // now cursor = 2 use = 1

        Thread.sleep(5000);
        System.out.println("\033[1;30m--------------------------------------------------------------------------------------------------remove event loop 1----------------\033[0m");
        eventLoopGroup.remove("my loop 1"); // now re-dispatches the health check clients, they all one loop 2 (cursor = 1 use = 0)
        System.out.println("\033[1;30m------------------------------------------------------------------------------------------------remove event loop 1 done-------------\033[0m");

        Thread.sleep(5000);
        System.out.println("\033[1;30m---------------------------------------------------------------------------------------------------close all servers-----------------\033[0m");
        serverA.close();
        serverB.close();
        System.out.println("\033[1;30m-------------------------------------------------------------------------------------------------close all servers done--------------\033[0m");

        Thread.sleep(5000);
        System.out.println("\033[1;30m--------------------------------------------------------------------------------------------------remove event loop 2----------------\033[0m");
        eventLoopGroup.remove("my loop 2");
        System.out.println("\033[1;30m------------------------------------------------------------------------------------------------remove event loop 2 done-------------\033[0m");

        eventLoop.close();
    }

    private static void runTimer(SelectorEventLoop eventLoop, ServerGroup grp) {
        eventLoop.delay(500, () -> {
            InetSocketAddress addr = grp.next();
            System.out.println("current active server is: \033[0;36m" + addr + "\033[0m");
            runTimer(eventLoop, grp);
        });
    }
}
