package net.cassite.vproxy.example;

import net.cassite.vproxy.connection.BindServer;
import net.cassite.vproxy.connection.NetEventLoop;
import net.cassite.vproxy.protocol.ProtocolHandler;
import net.cassite.vproxy.protocol.ProtocolHandlerContext;
import net.cassite.vproxy.protocol.ProtocolServerConfig;
import net.cassite.vproxy.protocol.ProtocolServerHandler;
import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.util.ByteArrayChannel;
import net.cassite.vproxy.util.RingBuffer;

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
            netEventLoop, BindServer.create(new InetSocketAddress("127.0.0.1", 18080)), config,
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
                    try {
                        inBuffer.writeTo(chnl);
                    } catch (IOException e) {
                        // should not happen
                    }
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
