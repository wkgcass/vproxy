package io.vproxy.adaptor.netty.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.vproxy.adaptor.netty.channel.VProxyEventLoopGroup;
import io.vproxy.adaptor.netty.channel.wrap.VProxyInetSocketChannel;
import io.vproxy.base.util.Logger;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class VProxyHttpClient {
    public static void main(String[] args) throws Exception {
        Bootstrap b = new Bootstrap();
        var group = new VProxyEventLoopGroup(1);
        b.group(group)
            .channel(VProxyInetSocketChannel.class)
            .handler(new ChannelInitializer<>() {
                @Override
                public void initChannel(Channel ch) {
                    ch.pipeline()
                        .addLast(new HttpResponseDecoder())
                        .addLast(new HttpRequestEncoder())
                        .addLast(new HttpObjectAggregator(1024 * 1024))
                        .addLast(new HttpClientHandler());
                }
            });

        var cf = b.connect(new InetSocketAddress("127.0.0.1", 8080));
        cf.addListener(f -> {
            if (f.cause() != null) {
                f.cause().printStackTrace();
            }
            var ch = cf.channel();
            var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello");
            request.headers().set("Connection", "close");

            ch.writeAndFlush(request);
        });
    }

    private static class HttpClientHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
        @Override
        public void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) {
            Logger.alert(response.content().toString(StandardCharsets.UTF_8));
            ctx.close().addListener(f -> System.exit(f.cause() == null ? 0 : 1));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
