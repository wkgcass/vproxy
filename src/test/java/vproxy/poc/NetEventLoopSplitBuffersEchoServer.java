package vproxy.poc;

import vproxy.connection.*;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.ByteArrayChannel;
import vproxy.util.RingBuffer;
import vproxy.util.Tuple;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.NetworkChannel;

/**
 * the server is almost the same as {@link NetEventLoopEchoServer} but splits inBuffer and outBuffer
 */
public class NetEventLoopSplitBuffersEchoServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        // create the event loop for network operations
        SelectorEventLoop selectorEventLoop = SelectorEventLoop.open();
        NetEventLoop eventLoop = new NetEventLoop(selectorEventLoop);
        // create server wrapper object
        ServerSock server = ServerSock.create(new InetSocketAddress(18080));
        // register the server into event loop
        eventLoop.addServer(server, null, new My2ServerHandler());
        // start loop in another thread
        new Thread(selectorEventLoop::loop, "EventLoopThread").start();

        Thread.sleep(500);
        AlphabetBlockingClient.runBlock(18080, 10, false);
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
    public Tuple<RingBuffer, RingBuffer> getIOBuffers(NetworkChannel channel) {
        // make the buffer small to demonstrate what will be done when buffer is full
        RingBuffer inBuffer = RingBuffer.allocateDirect(8);
        RingBuffer outBuffer = RingBuffer.allocateDirect(4);
        return new Tuple<>(inBuffer, outBuffer);
    }
}

class My2ConnectionHandler extends MyConnectionHandler implements ConnectionHandler {
    private final byte[] buffer = new byte[6]; // make it smaller than in-buffer and greater than out-buffer
    private ByteArrayChannel byteArrayChannel = ByteArrayChannel.fromEmpty(buffer);

    @Override
    public void readable(ConnectionHandlerContext ctx) {
        int writeBytes = ctx.connection.getInBuffer().writeTo(byteArrayChannel);
        if (writeBytes == 0) {
            System.out.println("writes nothing, either the in-buffer is empty or the buffer is full");
        } else {
            System.out.println("writes from buffer to outBuffer");
            writable(ctx); // try to write
        }
    }

    @Override
    public void writable(ConnectionHandlerContext ctx) {
        ctx.connection.getOutBuffer().storeBytesFrom(byteArrayChannel);
        if (byteArrayChannel.used() == 0) {
            System.out.println("everything wrote in writable callback, reset the ByteArrayChannel wrapper");
            byteArrayChannel.reset();
            readable(ctx); // try to read
        }
    }
}
