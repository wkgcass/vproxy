package vproxy.test.tool;

import vfd.IPPort;
import vproxybase.connection.NetEventLoop;
import vproxybase.connection.ServerSock;
import vproxybase.protocol.ProtocolHandler;
import vproxybase.protocol.ProtocolHandlerContext;
import vproxybase.protocol.ProtocolServerConfig;
import vproxybase.protocol.ProtocolServerHandler;
import vproxybase.util.nio.ByteArrayChannel;

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
            byte[] empty = new byte[size];
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
