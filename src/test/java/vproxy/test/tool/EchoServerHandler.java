package vproxy.test.tool;

import vfd.EventSet;
import vfd.ServerSocketFD;
import vfd.SocketFD;
import vproxy.selector.Handler;
import vproxy.selector.HandlerContext;

import java.io.IOException;

public class EchoServerHandler implements Handler<ServerSocketFD> {
    @Override
    public void accept(HandlerContext<ServerSocketFD> ctx) {
        final SocketFD client;
        try {
            client = ctx.getChannel().accept();
        } catch (IOException e) {
            // error occurred, remove from event loop
            ctx.remove();
            return;
        }
        try {
            ctx.getEventLoop().add(client, EventSet.read(), null, new EchoClientHandler());
        } catch (IOException e) {
            // error for adding this client
            // close the client
            try {
                client.close();
            } catch (IOException e1) {
                // we can do nothing about it
            }
        }
    }

    @Override
    public void connected(HandlerContext<ServerSocketFD> ctx) {
        // should not fire
    }

    @Override
    public void readable(HandlerContext<ServerSocketFD> ctx) {
        // should not fire
    }

    @Override
    public void writable(HandlerContext<ServerSocketFD> ctx) {
        // should not fire
    }

    @Override
    public void removed(HandlerContext<ServerSocketFD> ctx) {
        // removed from loop, let's close it
        ServerSocketFD svr = ctx.getChannel();
        try {
            svr.close();
        } catch (IOException e) {
            // we can do nothing about it
        }
        System.err.println("echo server closed");
    }
}
