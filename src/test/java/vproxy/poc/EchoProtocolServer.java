package vproxy.poc;

import vproxy.connection.NetEventLoop;
import vproxy.connection.ServerSock;
import vproxy.protocol.ProtocolHandler;
import vproxy.protocol.ProtocolHandlerContext;
import vproxy.protocol.ProtocolServerConfig;
import vproxy.protocol.ProtocolServerHandler;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.ByteArrayChannel;
import vproxy.util.RingBuffer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class EchoProtocolServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        SelectorEventLoop selectorEventLoop = SelectorEventLoop.open();
        NetEventLoop netEventLoop = new NetEventLoop(selectorEventLoop);

        // make the buffers small to demonstrate what will be done when buffer is full
        ProtocolServerConfig config = new ProtocolServerConfig();
        config.setInBufferSize(8);
        config.setOutBufferSize(4);

        ProtocolServerHandler.apply(
            netEventLoop, ServerSock.create(new InetSocketAddress("127.0.0.1", 18080)), config,
            new ProtocolHandler() {
                @Override
                public void init(ProtocolHandlerContext ctx) {
                    System.out.println("new connection");
                }

                @Override
                public void readable(ProtocolHandlerContext ctx) {
                    RingBuffer inBuffer = ctx.inBuffer;
                    int len = inBuffer.used();
                    byte[] bytes = new byte[len];
                    ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(bytes);
                    inBuffer.writeTo(chnl);
                    // the bytes array is filled with all bytes in inBuffer
                    // then we write the bytes back to client
                    ctx.write(bytes);
                }

                @Override
                public void exception(ProtocolHandlerContext ctx, Throwable err) {
                    System.err.println("connection got exception: " + err);
                    err.printStackTrace();
                }

                @Override
                public void end(ProtocolHandlerContext ctx) {
                    System.out.println("connection ends");
                }
            });

        new Thread(selectorEventLoop::loop).start();

        Thread.sleep(500);
        AlphabetBlockingClient.runBlock(18080, 10, false);

        selectorEventLoop.close();
    }
}
