package io.vproxy.adaptor.netty.test;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.AsciiString;
import io.vproxy.adaptor.netty.channel.VProxyEventLoopGroup;
import io.vproxy.base.util.Logger;
import io.vproxy.adaptor.netty.channel.wrap.VProxyInetServerSocketChannel;

public class VProxyHttpServer {
    public static void main(String[] args) throws Exception {
        var hostname = "127.0.0.1";
        var port = 8080;

        var acceptelg = new VProxyEventLoopGroup();
        var elg = new VProxyEventLoopGroup(4);
        var bootstrap = new ServerBootstrap();
        bootstrap
            .channel(VProxyInetServerSocketChannel.class)
            .childHandler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(new HttpServerCodec());
                    p.addLast(new HttpHelloWorldServerHandler());
                }
            });
        bootstrap.group(acceptelg, elg);
        bootstrap.bind(hostname, port).sync();
        Logger.alert("simple http server is listening on " + hostname + ":" + port);
    }
}

class HttpHelloWorldServerHandler extends ChannelInboundHandlerAdapter {
    private static final byte[] CONTENT = "Hello Netty on VProxy!\r\n".getBytes();
    private static final AsciiString CONTENT_LENGTH = new AsciiString("Content-Length");
    private static final AsciiString CONNECTION = new AsciiString("Connection");
    private static final AsciiString CLOSE = new AsciiString("Close");

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) msg;
            Logger.access(ctx.channel().remoteAddress() + " - " + req);

            FullHttpResponse response = new DefaultFullHttpResponse(
                req.protocolVersion(),
                HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(CONTENT));
            response.headers().setInt(CONTENT_LENGTH, response.content().readableBytes());

            response.headers().set(CONNECTION, CLOSE);
            ctx.write(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
