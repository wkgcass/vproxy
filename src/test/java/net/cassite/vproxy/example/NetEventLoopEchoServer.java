package net.cassite.vproxy.example;

import net.cassite.vproxy.connection.*;
import net.cassite.vproxy.connection.ServerHandler;
import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.util.RingBuffer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class NetEventLoopEchoServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        // create the event loop for network operations
        SelectorEventLoop selectorEventLoop = new SelectorEventLoop();
        NetEventLoop eventLoop = new NetEventLoop(selectorEventLoop);
        // create server socket channel and bind
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(18081));
        // create server wrapper object
        Server server = new Server(serverSocketChannel);
        // register the server into event loop
        eventLoop.addServer(server, null, new MyServerHandler());
        // start loop in another thread
        new Thread(eventLoop::loop, "EventLoopThread").start();

        Thread.sleep(500);
        EchoClient.runBlock(18081);
        selectorEventLoop.close();
    }
}

class MyServerHandler implements ServerHandler {
    @Override
    public void acceptFail(ServerHandlerContext ctx, IOException err) {
        // should not happen, only log here
        err.printStackTrace();
    }

    @Override
    public void connection(ServerHandlerContext ctx, Connection connection) {
        try {
            ctx.eventLoop.addConnection(connection, null, new MyConnectionHandler());
        } catch (IOException e) {
            System.err.println("got io exception when adding connection");
            e.printStackTrace();
        }
    }

    @Override
    public Connection getConnection(SocketChannel channel) {
        // make the buffer small to demonstrate what will be done when buffer is full
        RingBuffer buffer = RingBuffer.allocateDirect(8);
        try {
            return new Connection(channel,
                buffer, buffer // use the same buffer for input and output
            );
        } catch (IOException e) {
            // should not happen, only print exception log here
            e.printStackTrace();
        }
        return null; // the lib accepts null, and it will not invoke the `connection` callback
    }
}

class MyConnectionHandler implements ConnectionHandler {
    @Override
    public void readable(ConnectionHandlerContext ctx) {
        // we do not have to handle the event
        // the input and output buffer are the same
        // client input data will be write back to the client
        // which is exactly a echo server will act
    }

    @Override
    public void writable(ConnectionHandlerContext ctx) {
        // see comment in readable callback
    }

    @Override
    public void exception(ConnectionHandlerContext ctx, IOException err) {
        System.err.println("connection " + ctx.connection + " got exception");
        err.printStackTrace();
        ctx.connection.close(); // close channel should be handled manually here
    }

    @Override
    public void closed(ConnectionHandlerContext ctx) {
        // the event loop and channel handling is done by the lib
        // we only do log here
        System.err.println("connection closed " + ctx.connection);
    }
}
