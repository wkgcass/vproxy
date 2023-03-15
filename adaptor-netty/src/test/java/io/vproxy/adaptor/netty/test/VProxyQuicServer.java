package io.vproxy.adaptor.netty.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.quic.*;
import io.netty.util.CharsetUtil;
import io.vproxy.adaptor.netty.channel.VProxyEventLoopGroup;
import io.vproxy.base.util.Logger;
import io.vproxy.adaptor.netty.channel.VProxyDatagramFDChannel;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class VProxyQuicServer {
    public static void main(String[] args) throws Exception {
        SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate();
        QuicSslContext context = QuicSslContextBuilder.forServer(
                selfSignedCertificate.privateKey(), null, selfSignedCertificate.certificate())
            .applicationProtocols("http/0.9").build();
        VProxyEventLoopGroup group = new VProxyEventLoopGroup(1);
        ChannelHandler codec = new QuicServerCodecBuilder().sslContext(context)
            .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
            // Configure some limits for the maximal number of streams (and the data) that we want to handle.
            .initialMaxData(10000000)
            .initialMaxStreamDataBidirectionalLocal(1000000)
            .initialMaxStreamDataBidirectionalRemote(1000000)
            .initialMaxStreamsBidirectional(100)
            .initialMaxStreamsUnidirectional(100)

            // Setup a token handler. In a production system you would want to implement and provide your custom
            // one.
            .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
            // ChannelHandler that is added into QuicChannel pipeline.
            .handler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) {
                    QuicChannel channel = (QuicChannel) ctx.channel();
                    // Create streams etc..
                    Logger.alert("channelActive: " + channel);
                }

                public void channelInactive(ChannelHandlerContext ctx) {
                    ((QuicChannel) ctx.channel()).collectStats().addListener(f -> {
                        if (f.isSuccess()) {
                            Logger.alert("Connection closed: " + f.getNow());
                        }
                    });
                }

                @Override
                public boolean isSharable() {
                    return true;
                }
            })
            .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                @Override
                protected void initChannel(QuicStreamChannel ch) {
                    // Add a LineBasedFrameDecoder here as we just want to do some simple HTTP 0.9 handling.
                    ch.pipeline().addLast(new LineBasedFrameDecoder(1024))
                        .addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                ByteBuf byteBuf = (ByteBuf) msg;
                                Logger.access("received message: " + msg);
                                try {
                                    if (byteBuf.toString(CharsetUtil.US_ASCII).trim().equals("GET /")) {
                                        ByteBuf buffer = ctx.alloc().directBuffer();
                                        buffer.writeCharSequence("Hello Netty on VProxy!\r\n", CharsetUtil.US_ASCII);
                                        // Write the buffer and shutdown the output by writing a FIN.
                                        ctx.writeAndFlush(buffer).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                                    }
                                } finally {
                                    byteBuf.release();
                                }
                            }
                        });
                }
            }).build();

        var host = "127.0.0.1";
        var port = 9999;
        var ipport = host + ":" + port;
        Bootstrap bs = new Bootstrap();
        bs.group(group)
            .channel(VProxyDatagramFDChannel.class)
            .handler(codec);
        bs.bind(new InetSocketAddress(host, port)).await();
        Logger.alert("simple quic server is listening on " + ipport);
    }
}
