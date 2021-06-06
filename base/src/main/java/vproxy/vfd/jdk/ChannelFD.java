package vproxy.vfd.jdk;

import vproxy.vfd.FD;
import vproxy.vfd.type.FDCloseReq;
import vproxy.vfd.type.FDCloseReturn;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.NetworkChannel;
import java.nio.channels.SelectableChannel;

public class ChannelFD implements FD {
    private final SelectableChannel channel;

    public ChannelFD(SelectableChannel channel) {
        this.channel = channel;
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @SuppressWarnings("unused")
    protected class ChannelFDCloseReturn extends FDCloseReturn {
        protected ChannelFDCloseReturn(FDCloseReq req, DummyCall unused) throws IOException {
            super(req, dummyCall());
        }

        protected ChannelFDCloseReturn(FDCloseReq req, RealCall unused) throws IOException {
            super(req, dummyCall());
            close0(req);
        }

        protected ChannelFDCloseReturn(FDCloseReq req, SuperCall unused) throws IOException {
            super(req, realCall());
        }
    }

    private ChannelFDCloseReturn close0(FDCloseReq req) throws IOException {
        channel.close();
        return req.superClose(ChannelFDCloseReturn::new);
    }

    @Override
    public ChannelFDCloseReturn close(FDCloseReq req) throws IOException {
        return close0(req);
    }

    @Override
    public void configureBlocking(boolean b) throws IOException {
        channel.configureBlocking(b);
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        ((NetworkChannel) channel).setOption(name, value);
    }

    @Override
    public FD real() {
        return this;
    }

    public SelectableChannel getChannel() {
        return channel;
    }
}
