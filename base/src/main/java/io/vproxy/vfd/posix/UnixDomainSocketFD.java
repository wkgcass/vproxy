package vproxy.vfd.posix;

import vproxy.vfd.IPPort;
import vproxy.vfd.SocketFD;
import vproxy.vfd.UDSPath;

import java.io.IOException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;

public class UnixDomainSocketFD extends PosixNetworkFD implements SocketFD {
    private UDSPath local;
    private UDSPath remote;

    protected UnixDomainSocketFD(Posix posix) throws IOException {
        super(posix);
        this.fd = posix.createUnixDomainSocketFD();
    }

    protected UnixDomainSocketFD(Posix posix, int fd) {
        super(posix);
        this.fd = fd;
        connected = true;
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        if (name == StandardSocketOptions.SO_RCVBUF) {
            super.setOption(name, value);
        }
    }

    @Override
    public void connect(IPPort l4addr) throws IOException {
        if (!(l4addr instanceof UDSPath)) {
            throw new IOException("cannot use " + l4addr + " to establish a unix domain socket connection");
        }
        posix.connectUDS(fd, ((UDSPath) l4addr).path);
    }

    @Override
    public void shutdownOutput() throws IOException {
        checkConnected();
        checkNotClosed();
        posix.shutdownOutput(fd);
    }

    @Override
    public boolean finishConnect() throws IOException {
        checkNotClosed();
        posix.finishConnect(fd);
        connected = true;
        return true;
    }

    @Override
    public IPPort getLocalAddress() throws IOException {
        if (local == null) {
            checkNotClosed();
            local = (UDSPath) posix.getUDSLocal(fd).toIPPort();
        }
        return local;
    }

    @Override
    public IPPort getRemoteAddress() throws IOException {
        if (remote == null) {
            checkNotClosed();
            remote = (UDSPath) posix.getUDSRemote(fd).toIPPort();
        }
        return remote;
    }
}
