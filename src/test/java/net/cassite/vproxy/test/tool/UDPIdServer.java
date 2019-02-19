package net.cassite.vproxy.test.tool;

import net.cassite.vproxy.connection.*;
import net.cassite.vproxy.util.ByteArrayChannel;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.Tuple;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.NetworkChannel;

public class UDPIdServer {
    public final String id;

    public UDPIdServer(String id, NetEventLoop loop, int port) throws IOException {
        this(id, loop, port, "127.0.0.1");
    }

    public UDPIdServer(String id, NetEventLoop loop, int port, String host) throws IOException {
        this.id = id;
        BindServer bindServer = BindServer.createUDP(new InetSocketAddress(host, port));
        loop.addServer(bindServer, null, new UDPIdServerHandler());
    }

    class UDPIdServerHandler implements ServerHandler {
        @Override
        public void acceptFail(ServerHandlerContext ctx, IOException err) {
            // will not fire
        }

        @Override
        public void connection(ServerHandlerContext ctx, Connection connection) {
            // ignore
        }

        @Override
        public Tuple<RingBuffer, RingBuffer> getIOBuffers(NetworkChannel channel) {
            return new Tuple<>(RingBuffer.allocateDirect(32), RingBuffer.allocateDirect(32));
        }

        @Override
        public void removed(ServerHandlerContext ctx) {
            ctx.server.close();
        }

        @Override
        public void exception(ServerHandlerContext ctx, IOException err) {
            Logger.shouldNotHappen("got exception in udp id server", err);
            assert false;
        }

        @Override
        public ConnectionHandler udpHandler(ServerHandlerContext ctx, Connection conn) {
            return new UDPIdConnectionHandler();
        }
    }

    class UDPIdConnectionHandler implements ConnectionHandler {
        @Override
        public void readable(ConnectionHandlerContext ctx) {
            // ignore the input, just clear the buffer
            byte[] bytes = new byte[ctx.connection.inBuffer.used()];
            ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(bytes);
            ctx.connection.inBuffer.writeTo(chnl);

            bytes = id.getBytes();
            chnl = ByteArrayChannel.fromFull(bytes);
            ctx.connection.outBuffer.storeBytesFrom(chnl);
        }

        @Override
        public void writable(ConnectionHandlerContext ctx) {

        }

        @Override
        public void exception(ConnectionHandlerContext ctx, IOException err) {
            Logger.shouldNotHappen("got exception in udp id server conn", err);
            ctx.connection.close();
        }

        @Override
        public void closed(ConnectionHandlerContext ctx) {
            // ignore
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            ctx.connection.close();
        }
    }
}
