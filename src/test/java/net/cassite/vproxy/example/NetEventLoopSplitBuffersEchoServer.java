package net.cassite.vproxy.example;

import net.cassite.vproxy.connection.*;
import net.cassite.vproxy.connection.ServerHandler;
import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.util.ByteArrayChannel;
import net.cassite.vproxy.util.RingBuffer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * the server is almost the same as {@link NetEventLoopEchoServer} but splits inBuffer and outBuffer
 */
public class NetEventLoopSplitBuffersEchoServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        // create the event loop for network operations
        SelectorEventLoop selectorEventLoop = new SelectorEventLoop();
        NetEventLoop eventLoop = new NetEventLoop(selectorEventLoop);
        // create server socket channel and bind
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(18082));
        // create server wrapper object
        Server server = new Server(serverSocketChannel);
        // register the server into event loop
        eventLoop.addServer(server, null, new My2ServerHandler());
        // start loop in another thread
        new Thread(eventLoop::loop, "EventLoopThread").start();

        Thread.sleep(500);
        EchoClient.runBlock(18082);
        selectorEventLoop.close();
    }
}

class My2ServerHandler extends MyServerHandler implements ServerHandler {
    @Override
    public void connection(ServerHandlerContext ctx, Connection connection) {
        try {
            ctx.eventLoop.addConnection(connection, null, new My2ConnectionHandler());
        } catch (IOException e) {
            System.err.println("got io exception when adding connection");
            e.printStackTrace();
        }
    }

    @Override
    public Connection getConnection(SocketChannel channel) {
        // make the buffer small to demonstrate what will be done when buffer is full
        RingBuffer inBuffer = RingBuffer.allocateDirect(8);
        RingBuffer outBuffer = RingBuffer.allocateDirect(4);
        try {
            return new Connection(channel, inBuffer, outBuffer);
        } catch (IOException e) {
            // should not happen, only print exception log here
            e.printStackTrace();
        }
        return null; // the lib accepts null, and it will not invoke the `connection` callback
    }
}

class My2ConnectionHandler extends MyConnectionHandler implements ConnectionHandler {
    private final byte[] buffer = new byte[6]; // make it smaller than in-buffer and greater than out-buffer
    private ByteArrayChannel byteArrayChannel;

    @Override
    public void readable(ConnectionHandlerContext ctx) {
        if (byteArrayChannel == null) {
            byteArrayChannel = new ByteArrayChannel(buffer);
        }
        int writeBytes;
        try {
            writeBytes = ctx.connection.inBuffer.writeTo(byteArrayChannel);
        } catch (IOException e) {
            // should not happen, it's memory operation
            e.printStackTrace();
            return;
        }
        if (writeBytes == 0) {
            System.out.println("writes nothing, either the in-buffer is empty or the buffer is full");
        } else {
            System.out.println("writes from buffer to outBuffer");
            writable(ctx); // try to write
        }
    }

    @Override
    public void writable(ConnectionHandlerContext ctx) {
        if (byteArrayChannel != null) {
            try {
                ctx.connection.outBuffer.storeBytesFrom(byteArrayChannel);
            } catch (IOException e) {
                // should not happen, it's memory operation
                e.printStackTrace();
                return;
            }
            if (byteArrayChannel.used() == 0) {
                System.out.println("everything wrote in writable callback, remove the ByteArrayChannel wrapper");
                byteArrayChannel = null;
                readable(ctx); // try to read
            }
        }
    }
}
