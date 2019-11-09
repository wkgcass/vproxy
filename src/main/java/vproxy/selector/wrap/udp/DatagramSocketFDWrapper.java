package vproxy.selector.wrap.udp;

import vfd.DatagramFD;
import vfd.FD;
import vfd.SocketFD;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;

public final class DatagramSocketFDWrapper implements SocketFD {
    private final DatagramFD fd;
    private boolean connected = false;

    public DatagramSocketFDWrapper(DatagramFD fd) {
        this.fd = fd;
    }

    @Override
    public void connect(InetSocketAddress l4addr) throws IOException {
        fd.connect(l4addr);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void shutdownOutput() {
        // do nothing
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return fd.getLocalAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        return fd.getRemoteAddress();
    }

    @Override
    public boolean finishConnect() {
        connected = true;
        return true;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return fd.read(dst);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return fd.write(src);
    }

    @Override
    public void configureBlocking(boolean b) throws IOException {
        fd.configureBlocking(b);
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        fd.setOption(name, value);
    }

    @Override
    public FD real() {
        return fd.real();
    }

    @Override
    public boolean isOpen() {
        return fd.isOpen();
    }

    @Override
    public void close() throws IOException {
        fd.close();
    }
}
