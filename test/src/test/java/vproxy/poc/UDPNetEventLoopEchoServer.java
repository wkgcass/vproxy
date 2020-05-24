package vproxy.poc;

import vfd.IPPort;
import vproxybase.connection.*;
import vproxybase.selector.wrap.udp.UDPFDs;
import vproxybase.util.RingBuffer;
import vproxybase.util.nio.ByteArrayChannel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class UDPNetEventLoopEchoServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        NetEventLoop loop = NetEventLoopEchoServer.create(18080, UDPFDs.get());

        ConnectableConnection conn = ConnectableConnection.createUDP(new IPPort(18080),
            new ConnectionOpts(), RingBuffer.allocateDirect(1024), RingBuffer.allocateDirect(3));
        loop.addConnectableConnection(conn, null, new EchoClientConnectableConnectionHandler());

        Thread.sleep(10_000);
        loop.getSelectorEventLoop().close();
    }
}

class EchoClientConnectableConnectionHandler implements ConnectableConnectionHandler {
    private static final String SEND = "hello-world-";
    private final ByteArrayChannel readChnl = ByteArrayChannel.fromEmpty(1024);
    private ByteArrayChannel writeChnl = null;
    private int currentPostfix = 0;
    private String currentSend = SEND + (currentPostfix++);

    @Override
    public void connected(ConnectableConnectionHandlerContext ctx) {
        writeChnl = ByteArrayChannel.fromFull(currentSend.getBytes());
        ctx.connection.getOutBuffer().storeBytesFrom(writeChnl);
    }

    @Override
    public void readable(ConnectionHandlerContext ctx) {
        ctx.connection.getInBuffer().writeTo(readChnl);
        String s = new String(readChnl.getBytes(), 0, readChnl.used(), StandardCharsets.UTF_8);
        System.out.println("client read \033[0;32m" + s + "\033[0m");
        if (s.equals(currentSend)) {
            currentSend = SEND + (currentPostfix++);
            readChnl.reset();
            writeChnl = ByteArrayChannel.fromFull(currentSend.getBytes());
            ctx.connection.getOutBuffer().storeBytesFrom(writeChnl);
        }
    }

    @Override
    public void writable(ConnectionHandlerContext ctx) {
        if (writeChnl != null) {
            if (writeChnl.used() == 0) {
                writeChnl = null;
                return;
            }
            ctx.connection.getOutBuffer().storeBytesFrom(writeChnl);
        }
    }

    @Override
    public void exception(ConnectionHandlerContext ctx, IOException err) {
        err.printStackTrace();
    }

    @Override
    public void remoteClosed(ConnectionHandlerContext ctx) {
        // ignore
    }

    @Override
    public void closed(ConnectionHandlerContext ctx) {
        // ignored
    }

    @Override
    public void removed(ConnectionHandlerContext ctx) {
        // ignored
    }
}
