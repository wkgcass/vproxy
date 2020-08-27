package vfd.posix;

import vfd.IPPort;
import vfd.SocketFD;
import vfd.UDSPath;

import java.io.IOException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;

public class UnixDomainSocketFD extends PosixNetworkFD implements SocketFD {
    private UDSPath local;
    private UDSPath remote;

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
    public void connect(IPPort l4addr) {
        throw new UnsupportedOperationException("not supported yet");
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
