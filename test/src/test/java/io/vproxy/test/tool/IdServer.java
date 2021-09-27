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
import java.util.concurrent.atomic.AtomicLong;

public class IdServer {
    public final AtomicLong accepted = new AtomicLong();
    public final AtomicLong inBytes = new AtomicLong();
    public final AtomicLong outBytes = new AtomicLong();
    public final AtomicLong current = new AtomicLong();

    private final String id;

    public IdServer(String id, NetEventLoop loop, int port) throws IOException {
        this(id, loop, port, "127.0.0.1");
    }

    public IdServer(String id, NetEventLoop loop, int port, String addr) throws IOException {
        this.id = id;
        ServerSock serverSocks = ServerSock.create(new IPPort(addr, port));
        ProtocolServerHandler.apply(loop, serverSocks,
            new ProtocolServerConfig(), new IdProtocolHandler());
    }

    class IdProtocolHandler implements ProtocolHandler<Object> {
        @Override
        public void init(ProtocolHandlerContext<Object> ctx) {
            accepted.incrementAndGet();
            current.incrementAndGet();
        }

        @Override
        public void readable(ProtocolHandlerContext<Object> ctx) {
            int size = ctx.inBuffer.used();
            byte[] empty = Utils.allocateByteArray(size);
            ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(empty);
            ctx.inBuffer.writeTo(chnl);
            inBytes.addAndGet(size);
            // respond the id
            byte[] toWrite = id.getBytes();
            ctx.write(toWrite);
            outBytes.addAndGet(toWrite.length);
        }

        @Override
        public void exception(ProtocolHandlerContext<Object> ctx, Throwable err) {
            // ignore exceptions
        }

        @Override
        public void end(ProtocolHandlerContext<Object> ctx) {
            current.decrementAndGet();
        }
    }
}
