package vfd.jdk;

import vfd.DatagramFD;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class DatagramChannelFD extends ChannelFD implements DatagramFD {
    private final DatagramChannel channel;

    public DatagramChannelFD(DatagramChannel channel) {
        super(channel);
        this.channel = channel;
    }

    @Override
    public void connect(InetSocketAddress l4addr) throws IOException {
        channel.connect(l4addr);
    }

    @Override
    public void bind(InetSocketAddress l4addr) throws IOException {
        channel.bind(l4addr);
    }

    @Override
    public int send(ByteBuffer buf, SocketAddress remote) throws IOException {
        return channel.send(buf, remote);
    }

    @Override
    public SocketAddress receive(ByteBuffer buf) throws IOException {
        return channel.receive(buf);
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return channel.getLocalAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        return channel.getRemoteAddress();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return channel.read(dst);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return channel.write(src);
    }

    @Override
    public void configureBlocking(boolean b) throws IOException {
        channel.configureBlocking(b);
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        channel.setOption(name, value);
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    @Override
    public DatagramChannel getChannel() {
        return channel;
    }
}
