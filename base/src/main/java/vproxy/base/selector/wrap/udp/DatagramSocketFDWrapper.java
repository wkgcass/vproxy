package vproxy.base.selector.wrap.udp;

import vproxy.vfd.DatagramFD;
import vproxy.vfd.FD;
import vproxy.vfd.IPPort;
import vproxy.vfd.SocketFD;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.ByteBuffer;

public final class DatagramSocketFDWrapper implements SocketFD {
    private final DatagramFD fd;
    private boolean connected = false;

    public DatagramSocketFDWrapper(DatagramFD fd) {
        this.fd = fd;
    }

    @Override
    public void connect(IPPort l4addr) throws IOException {
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
    public IPPort getLocalAddress() throws IOException {
        return fd.getLocalAddress();
    }

    @Override
    public IPPort getRemoteAddress() throws IOException {
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
    public boolean contains(FD fd) {
        return this.fd == fd || this.fd.contains(fd);
    }

    @Override
    public boolean isOpen() {
        return fd.isOpen();
    }

    @Override
    public void close() throws IOException {
        fd.close();
    }

    @Override
    public String toString() {
        return "DatagramSocketFDWrapper(" + fd + ")";
    }
}
