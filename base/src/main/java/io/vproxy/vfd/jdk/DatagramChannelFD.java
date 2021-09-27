package vproxy.vfd.jdk;

import vproxy.vfd.DatagramFD;
import vproxy.vfd.IPPort;

import java.io.IOException;
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
    public void connect(IPPort l4addr) throws IOException {
        channel.connect(l4addr.toInetSocketAddress());
    }

    @Override
    public void bind(IPPort l4addr) throws IOException {
        channel.bind(l4addr.toInetSocketAddress());
    }

    @Override
    public int send(ByteBuffer buf, IPPort remote) throws IOException {
        return channel.send(buf, remote.toInetSocketAddress());
    }

    @Override
    public IPPort receive(ByteBuffer buf) throws IOException {
        return IPPort.fromNullable(channel.receive(buf));
    }

    @Override
    public IPPort getLocalAddress() throws IOException {
        return IPPort.fromNullable(channel.getLocalAddress());
    }

    @Override
    public IPPort getRemoteAddress() throws IOException {
        return IPPort.fromNullable(channel.getRemoteAddress());
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
    public DatagramChannel getChannel() {
        return channel;
    }

    @Override
    public String toString() {
        try {
            return "DatagramChannelFD(local=" + channel.getLocalAddress() + ", remote=" + channel.getRemoteAddress() + ")";
        } catch (IOException e) {
            return "DatagramChannelFD(" + channel + ")";
        }
    }
}
