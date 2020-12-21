package vproxy.poc;

import vfd.IPPort;
import vproxybase.component.check.HealthCheckConfig;
import vproxybase.component.elgroup.EventLoopGroup;
import vproxybase.component.elgroup.EventLoopWrapper;
import vproxybase.component.svrgroup.Method;
import vproxybase.component.svrgroup.ServerGroup;
import vproxybase.connection.Connection;
import vproxybase.connection.Connector;
import vproxybase.connection.ServerSock;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.util.VProxyThread;
import vproxybase.util.exception.AlreadyExistException;
import vproxybase.util.exception.ClosedException;
import vproxybase.util.exception.NotFoundException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ServerGroupExample {
    public static void main(String[] args) throws AlreadyExistException, IOException, ClosedException, InterruptedException, NotFoundException {
        int portA = 18080;
        int portB = 18081;

        EventLoopGroup eventLoopGroup = new EventLoopGroup("my group");
        ServerGroup serverGroup = new ServerGroup("server group", eventLoopGroup,
            new HealthCheckConfig(200, 800, 4, 5),
            Method.wrr);
        serverGroup.add("s1", new IPPort("127.0.0.1", portA), 5);
        serverGroup.add("s2", new IPPort("127.0.0.1", portB), 10);

        // create a event loop only for checking
        SelectorEventLoop eventLoop = SelectorEventLoop.open();
        runTimer(eventLoop, eventLoopGroup, serverGroup);
        new VProxyThread(eventLoop::loop, "for-checking").start();

        eventLoopGroup.add("my loop 1"); // re-dispatch to all threads (currently 1 thread ),
        // cursor = 1 use = 0
        eventLoopGroup.add("my loop 2"); // re-dispatch to all threads (currently 2 threads),
        // for serverA cursor = 2 use = 1, for serverB cursor = 1 use = 0

        System.out.println("\033[1;30m---------------------------------------------------------------------------------------server group created (s1 5, s2 10)------------\033[0m");

        Thread.sleep(5000);
        System.out.println("\033[1;30m--------------------------------------------------------------------------------------------create serverA on " + portA + "----------\033[0m");
        SelectorEventLoop serverA = SelectorEventLoopEchoServer.createServer(portA);
        new VProxyThread(serverA::loop, "serverA Event Loop Thread").start();

        Thread.sleep(5000);
        System.out.println("\033[1;30m--------------------------------------------------------------------------------------------create serverB on " + portB + "----------\033[0m");
        SelectorEventLoop serverB = SelectorEventLoopEchoServer.createServer(portB);
        new VProxyThread(serverB::loop, "serverB Event Loop Thread").start();

        Thread.sleep(20000);
        System.out.println("\033[1;30m---------------------------------------------------------------------------------------------let's remove serverA from group---------\033[0m");
        serverGroup.remove("s1");

        Thread.sleep(5000);
        System.out.println("\033[1;30m--------------------------------------------------------------------------------------------let's add serverA back with 10-----------\033[0m");
        serverGroup.add("s1", new IPPort("127.0.0.1", portA), 10); // now cursor = 2 use = 1

        Thread.sleep(20000);
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

    private static void runTimer(SelectorEventLoop eventLoop, EventLoopGroup eventLoopGroup, ServerGroup grp) {
        eventLoop.delay(500, () -> {
            Connector c = grp.next(null);
            System.out.println("current active server is: \033[0;36m" + c + "\033[0m");
            List<String> names = eventLoopGroup.names();
            for (String name : names) {
                EventLoopWrapper w;
                try {
                    w = eventLoopGroup.get(name);
                } catch (NotFoundException e) {
                    // won't happen
                    continue;
                }
                List<ServerSock> serverSocks = new ArrayList<>();
                w.copyServers(serverSocks);
                List<Connection> connections = new ArrayList<>();
                w.copyConnections(connections);
                System.out.println("event loop \033[0;36m" + w.alias + "\033[0m: server-socks: \033[0;36m" + serverSocks + "\033[0m, connections: \033[0;36m" + connections + "\033[0m");
            }
            runTimer(eventLoop, eventLoopGroup, grp);
        });
    }
}
