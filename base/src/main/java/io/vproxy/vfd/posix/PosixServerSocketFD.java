package io.vproxy.vfd.posix;

import io.vproxy.vfd.*;

import java.io.IOException;

public class PosixServerSocketFD extends PosixFD implements ServerSocketFD {
    private IPPort local;

    protected PosixServerSocketFD(Posix posix) {
        super(posix);
    }

    @Override
    public IPPort getLocalAddress() {
        return local;
    }

    @Override
    public SocketFD accept() throws IOException {
        checkFD();
        checkNotClosed();
        int subFd = posix.accept(fd);
        if (subFd == 0) {
            return null;
        }
        return new PosixSocketFD(posix, subFd, local.getAddress() instanceof IPv4);
    }

    @Override
    public void bind(IPPort l4addr) throws IOException {
        checkNotClosed();
        if (local != null) {
            throw new IOException("already bond " + local);
        }
        int port = l4addr.getPort();
        if (l4addr.getAddress() instanceof IPv4) {
            fd = posix.createIPv4TcpFD();
            finishConfigAfterFDCreated();
            int ipv4 = IP.ipv4Bytes2Int(l4addr.getAddress().getAddress());
            posix.bindIPv4(fd, ipv4, port);
        } else if (l4addr.getAddress() instanceof IPv6) {
            fd = posix.createIPv6TcpFD();
            finishConfigAfterFDCreated();
            String ipv6 = ((IPv6) l4addr.getAddress()).formatToIPStringWithoutBrackets();
            posix.bindIPv6(fd, ipv6, port);
        } else {
            throw new IOException("unknown l3addr " + l4addr.getAddress());
        }
        this.local = l4addr;
    }
}
