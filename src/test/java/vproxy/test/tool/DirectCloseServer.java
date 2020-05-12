package vproxy.test.tool;

import vfd.IPPort;
import vfd.SocketFD;
import vproxy.connection.*;
import vproxy.util.RingBuffer;
import vproxy.util.Tuple;

import java.io.IOException;

public class DirectCloseServer {
    public DirectCloseServer(NetEventLoop loop, int port) throws IOException {
        this(loop, port, "127.0.0.1");
    }

    public DirectCloseServer(NetEventLoop loop, int port, String addr) throws IOException {
        ServerSock serverSock = ServerSock.create(new IPPort(addr, port));
        loop.addServer(serverSock, null, new CloseHandler());
    }

    static class CloseHandler implements ServerHandler {
        @Override
        public void acceptFail(ServerHandlerContext ctx, IOException err) {
            // ignore
        }

        @Override
        public void connection(ServerHandlerContext ctx, Connection connection) {
            // ignore
        }

        @Override
        public Tuple<RingBuffer, RingBuffer> getIOBuffers(SocketFD channel) {
            // return null, then the lib will close the connection
            return null;
        }

        @Override
        public void removed(ServerHandlerContext ctx) {
            ctx.server.close();
        }
    }
}
