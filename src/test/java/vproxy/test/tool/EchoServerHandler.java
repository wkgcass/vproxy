package vproxy.test.tool;

import vproxy.selector.Handler;
import vproxy.selector.HandlerContext;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class EchoServerHandler implements Handler<ServerSocketChannel> {
    @Override
    public void accept(HandlerContext<ServerSocketChannel> ctx) {
        final SocketChannel client;
        try {
            client = ctx.getChannel().accept();
        } catch (IOException e) {
            // error occurred, remove from event loop
            ctx.remove();
            return;
        }
        try {
            ctx.getEventLoop().add(client, SelectionKey.OP_READ, null, new EchoClientHandler());
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
    public void connected(HandlerContext<ServerSocketChannel> ctx) {
        // should not fire
    }

    @Override
    public void readable(HandlerContext<ServerSocketChannel> ctx) {
        // should not fire
    }

    @Override
    public void writable(HandlerContext<ServerSocketChannel> ctx) {
        // should not fire
    }

    @Override
    public void removed(HandlerContext<ServerSocketChannel> ctx) {
        // removed from loop, let's close it
        ServerSocketChannel svr = ctx.getChannel();
        try {
            svr.close();
        } catch (IOException e) {
            // we can do nothing about it
        }
        System.err.println("echo server closed");
    }
}
