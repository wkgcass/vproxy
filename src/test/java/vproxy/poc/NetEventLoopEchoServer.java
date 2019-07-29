package vproxy.poc;

import vproxy.connection.*;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.RingBuffer;
import vproxy.util.Tuple;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.NetworkChannel;

public class NetEventLoopEchoServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        // create the event loop for network operations
        SelectorEventLoop selectorEventLoop = SelectorEventLoop.open();
        NetEventLoop eventLoop = new NetEventLoop(selectorEventLoop);
        // create server wrapper object
        BindServer server = BindServer.create(new InetSocketAddress(18080));
        // register the server into event loop
        eventLoop.addServer(server, null, new MyServerHandler());
        // start loop in another thread
        new Thread(selectorEventLoop::loop, "EventLoopThread").start();

        Thread.sleep(500);
        AlphabetBlockingClient.runBlock(18080, 10, false);
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
    public Tuple<RingBuffer, RingBuffer> getIOBuffers(NetworkChannel channel) {
        // make the buffer small to demonstrate what will be done when buffer is full
        RingBuffer buffer = RingBuffer.allocateDirect(8);
        return new Tuple<>(buffer, buffer); // use the same buffer for input and output
    }

    @Override
    public void removed(ServerHandlerContext ctx) {
        System.err.println("echo server closed");
        ctx.server.close();
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
    public void remoteClosed(ConnectionHandlerContext ctx) {
        System.out.println("remote closed called " + ctx.connection);
        ctx.connection.close();
    }

    @Override
    public void closed(ConnectionHandlerContext ctx) {
        // the event loop and channel handling is done by the lib
        // we only do log here
        System.err.println("connection closed " + ctx.connection);
    }

    @Override
    public void removed(ConnectionHandlerContext ctx) {
        ctx.connection.close();
        // it's ok if the connection is already closed
        // the close method will check first then do real close
    }
}
