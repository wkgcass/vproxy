package io.vproxy.adaptor.netty.channel;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.RecvByteBufAllocator;

public class VProxyChannelConfig extends DefaultChannelConfig implements ChannelConfig {
    public VProxyChannelConfig(Channel channel) {
        super(channel);
    }

    protected VProxyChannelConfig(Channel channel, RecvByteBufAllocator allocator) {
        super(channel, allocator);
    }

    @Override
    public ByteBufAllocator getAllocator() {
        return channel.alloc();
    }
}
