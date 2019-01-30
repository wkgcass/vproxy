package net.cassite.vproxy.test.tool;

import net.cassite.vproxy.connection.BindServer;
import net.cassite.vproxy.connection.NetEventLoop;
import net.cassite.vproxy.protocol.ProtocolHandler;
import net.cassite.vproxy.protocol.ProtocolHandlerContext;
import net.cassite.vproxy.protocol.ProtocolServerConfig;
import net.cassite.vproxy.protocol.ProtocolServerHandler;
import net.cassite.vproxy.util.ByteArrayChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
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
        BindServer bindServer = BindServer.create(new InetSocketAddress(addr, port));
        ProtocolServerHandler.apply(loop, bindServer,
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
            byte[] empty = new byte[size];
            ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(empty);
            try {
                ctx.inBuffer.writeTo(chnl);
            } catch (IOException e) {
                // should not happen
                // it's memory operation
                return;
            }
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
