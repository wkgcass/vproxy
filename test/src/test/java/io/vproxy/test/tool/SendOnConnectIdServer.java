package io.vproxy.test.tool;

import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.connection.ServerSock;
import io.vproxy.base.protocol.ProtocolHandler;
import io.vproxy.base.protocol.ProtocolHandlerContext;
import io.vproxy.base.protocol.ProtocolServerConfig;
import io.vproxy.base.protocol.ProtocolServerHandler;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.vfd.IPPort;

import java.io.IOException;

public class SendOnConnectIdServer {
    private final String id;

    public SendOnConnectIdServer(String id, NetEventLoop loop, int port) throws IOException {
        this(id, loop, port, "127.0.0.1");
    }

    public SendOnConnectIdServer(String id, NetEventLoop loop, int port, String addr) throws IOException {
        this.id = id;
        ServerSock serverSock = ServerSock.create(new IPPort(addr, port));
        ProtocolServerHandler.apply(loop, serverSock,
            new ProtocolServerConfig(), new IdProtocolHandler());
    }

    class IdProtocolHandler implements ProtocolHandler<Object> {
        @Override
        public void init(ProtocolHandlerContext<Object> ctx) {
            // respond the id
            byte[] toWrite = id.getBytes();
            ctx.write(toWrite);
        }

        @Override
        public void readable(ProtocolHandlerContext<Object> ctx) {
            // clear input data
            int size = ctx.inBuffer.used();
            byte[] empty = Utils.allocateByteArray(size);
            ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(empty);
            ctx.inBuffer.writeTo(chnl);
        }

        @Override
        public void exception(ProtocolHandlerContext<Object> ctx, Throwable err) {
            // ignore exceptions
        }

        @Override
        public void end(ProtocolHandlerContext<Object> ctx) {
            // ignore
        }
    }
}
