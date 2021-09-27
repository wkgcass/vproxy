package io.vproxy.test.tool;

import io.vproxy.base.connection.*;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.SocketFD;

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
