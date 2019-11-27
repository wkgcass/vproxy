package vfd.posix;

import vfd.ServerSocketFD;
import vfd.SocketFD;
import vproxy.util.Utils;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class PosixServerSocketFD extends PosixFD implements ServerSocketFD {
    private InetSocketAddress local;

    protected PosixServerSocketFD(Posix posix) {
        super(posix);
    }

    @Override
    public SocketAddress getLocalAddress() {
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
        return new PosixSocketFD(posix, subFd, local.getAddress() instanceof Inet4Address);
    }

    @Override
    public void bind(InetSocketAddress l4addr) throws IOException {
        checkNotClosed();
        if (local != null) {
            throw new IOException("already bond " + local);
        }
        int port = l4addr.getPort();
        if (l4addr.getAddress() instanceof Inet4Address) {
            fd = posix.createIPv4TcpFD();
            finishConfigAfterFDCreated();
            int ipv4 = Utils.ipv4Bytes2Int(l4addr.getAddress().getAddress());
            posix.bindIPv4(fd, ipv4, port);
        } else if (l4addr.getAddress() instanceof Inet6Address) {
            fd = posix.createIPv6TcpFD();
            finishConfigAfterFDCreated();
            String ipv6 = Utils.ipStr(l4addr.getAddress().getAddress());
            posix.bindIPv6(fd, ipv6, port);
        } else {
            throw new IOException("unknown l3addr " + l4addr.getAddress());
        }
        this.local = l4addr;
    }
}
