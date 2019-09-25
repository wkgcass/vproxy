package vproxy.test.tool;

import vproxy.connection.*;
import vproxy.util.RingBuffer;
import vproxy.util.Tuple;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.NetworkChannel;

public class DirectCloseServer {
    public DirectCloseServer(NetEventLoop loop, int port) throws IOException {
        this(loop, port, "127.0.0.1");
    }

    public DirectCloseServer(NetEventLoop loop, int port, String addr) throws IOException {
        ServerSock serverSock = ServerSock.create(new InetSocketAddress(addr, port));
        loop.addServer(serverSock, null, new CloseHandler());
    }

    class CloseHandler implements ServerHandler {
        @Override
        public void acceptFail(ServerHandlerContext ctx, IOException err) {
            // ignore
        }

        @Override
        public void connection(ServerHandlerContext ctx, Connection connection) {
            // ignore
        }

        @Override
        public Tuple<RingBuffer, RingBuffer> getIOBuffers(NetworkChannel channel) {
            // return null, then the lib will close the connection
            return null;
        }

        @Override
        public void removed(ServerHandlerContext ctx) {
            ctx.server.close();
        }
    }
}
