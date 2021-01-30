package vproxy.poc;

import vfd.IPPort;
import vproxybase.connection.NetEventLoop;
import vproxybase.connection.ServerSock;
import vproxybase.protocol.ProtocolHandler;
import vproxybase.protocol.ProtocolHandlerContext;
import vproxybase.protocol.ProtocolServerConfig;
import vproxybase.protocol.ProtocolServerHandler;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.util.RingBuffer;
import vproxybase.util.Utils;
import vproxybase.util.thread.VProxyThread;
import vproxybase.util.nio.ByteArrayChannel;

import java.io.IOException;

public class EchoProtocolServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        SelectorEventLoop selectorEventLoop = SelectorEventLoop.open();
        NetEventLoop netEventLoop = new NetEventLoop(selectorEventLoop);

        // make the buffers small to demonstrate what will be done when buffer is full
        ProtocolServerConfig config = new ProtocolServerConfig();
        config.setInBufferSize(8);
        config.setOutBufferSize(4);

        ProtocolServerHandler.apply(
            netEventLoop, ServerSock.create(new IPPort("127.0.0.1", 18080)), config,
            new ProtocolHandler() {
                @Override
                public void init(ProtocolHandlerContext ctx) {
                    System.out.println("new connection");
                }

                @Override
                public void readable(ProtocolHandlerContext ctx) {
                    RingBuffer inBuffer = ctx.inBuffer;
                    int len = inBuffer.used();
                    byte[] bytes = Utils.allocateByteArray(len);
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

        VProxyThread.create(selectorEventLoop::loop, "echo-protocol-server").start();

        Thread.sleep(500);
        AlphabetBlockingClient.runBlock(18080, 10, false);

        selectorEventLoop.close();
    }
}
