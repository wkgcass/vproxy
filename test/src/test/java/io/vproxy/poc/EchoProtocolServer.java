package vproxy.poc;

import vproxy.base.connection.NetEventLoop;
import vproxy.base.connection.ServerSock;
import vproxy.base.protocol.ProtocolHandler;
import vproxy.base.protocol.ProtocolHandlerContext;
import vproxy.base.protocol.ProtocolServerConfig;
import vproxy.base.protocol.ProtocolServerHandler;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.RingBuffer;
import vproxy.base.util.Utils;
import vproxy.base.util.nio.ByteArrayChannel;
import vproxy.base.util.thread.VProxyThread;
import vproxy.vfd.IPPort;

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
