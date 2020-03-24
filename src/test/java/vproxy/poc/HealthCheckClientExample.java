package vproxy.poc;

import vproxy.component.check.*;
import vproxy.connection.NetEventLoop;
import vproxy.selector.SelectorEventLoop;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class HealthCheckClientExample {
    public static void main(String[] args) throws IOException, InterruptedException {
        SelectorEventLoop loop = SelectorEventLoop.open();
        NetEventLoop eventLoop = new NetEventLoop(loop);
        HealthCheckClient client = new HealthCheckClient(eventLoop,
            new InetSocketAddress("127.0.0.1", 18080),
            new HealthCheckConfig(200, 800, 4, 5),
            new AnnotatedHcConfig(),
            true, new HealthCheckHandler() {
            @Override
            public void up(SocketAddress remote) {
                System.out.println("health check status change to \033[1;32mup\033[0m");
            }

            @Override
            public void down(SocketAddress remote, String reason) {
                System.out.println("health check status change to \033[1;31mdown\033[0m, " + reason);
            }

            @Override
            public void upOnce(SocketAddress remote, ConnectResult cost) {
                System.out.println("health check got \033[0;32mone up\033[0m");
            }

            @Override
            public void downOnce(SocketAddress remote, String reason) {
                System.out.println("health check got \033[0;31mone down\033[0m, " + reason);
            }
        });
        client.start();
        new Thread(loop::loop, "ClientEventLoop").start();

        System.out.println("\033[1;30m-------------------wait for 5 seconds then start server-------------------\033[0;30m");
        Thread.sleep(5000);
        System.out.println("\033[1;30m-----------------------------start server---------------------------------\033[0;30m");
        SelectorEventLoop[] serverLoop = {SelectorEventLoopEchoServer.createServer(18080)};
        new Thread(() -> serverLoop[0].loop(), "ServerEventLoop").start();

        Thread.sleep(5000);
        System.out.println("\033[1;30m-----------------------------stop server----------------------------------\033[0;30m");
        serverLoop[0].close();

        Thread.sleep(5000);
        System.out.println("\033[1;30m--------let's see what happen if server starts and closes rapidly---------\033[0;30m");

        serverLoop[0] = SelectorEventLoopEchoServer.createServer(18080);
        new Thread(() -> serverLoop[0].loop(), "ServerEventLoop").start();
        Thread.sleep(2000);
        serverLoop[0].close();
        Thread.sleep(3000);
        serverLoop[0] = SelectorEventLoopEchoServer.createServer(18080);
        new Thread(() -> serverLoop[0].loop(), "ServerEventLoop").start();
        Thread.sleep(2000);
        serverLoop[0].close();

        loop.close();
    }
}
