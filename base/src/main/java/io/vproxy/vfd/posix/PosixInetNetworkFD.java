package io.vproxy.vfd.posix;

import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.IPv6;

import java.io.IOException;

public class PosixInetNetworkFD extends PosixNetworkFD {
    protected boolean ipv4 = false;

    private IPPort local;
    private IPPort remote;

    protected PosixInetNetworkFD(Posix posix) {
        super(posix);
    }

    protected int createIPv4FD() throws IOException {
        return posix.createIPv4TcpFD();
    }

    protected int createIPv6FD() throws IOException {
        return posix.createIPv6TcpFD();
    }

    public void connect(IPPort l4addr) throws IOException {
        checkNotClosed();
        if (fd != -1) {
            throw new IOException("cannot call connect()");
        }
        if (l4addr.getAddress() instanceof IPv4) {
            byte[] host = l4addr.getAddress().getAddress();
            assert host.length == 4;

            fd = createIPv4FD();
            finishConfigAfterFDCreated();
            int n = IP.ipv4Bytes2Int(host);
            posix.connectIPv4(fd, n, l4addr.getPort());
            ipv4 = true;
        } else if (l4addr.getAddress() instanceof IPv6) {
            fd = createIPv6FD();
            finishConfigAfterFDCreated();
            String str = ((IPv6) l4addr.getAddress()).formatToIPStringWithoutBrackets();
            posix.connectIPv6(fd, str, l4addr.getPort());
            ipv4 = false;
        } else {
            throw new IOException("unknown l3addr " + l4addr.getAddress());
        }
        // record the remote addr
        remote = l4addr;
    }

    public IPPort getLocalAddress() throws IOException {
        checkFD();
        if (local == null) {
            checkNotClosed();
            if (ipv4) {
                local = posix.getIPv4Local(fd).toIPPort();
            } else {
                local = posix.getIPv6Local(fd).toIPPort();
            }
        }
        return local;
    }

    public IPPort getRemoteAddress() throws IOException {
        checkFD();
        if (remote == null) {
            checkNotClosed();
            if (ipv4) {
                remote = posix.getIPv4Remote(fd).toIPPort();
            } else {
                remote = posix.getIPv6Remote(fd).toIPPort();
            }
        }
        return remote;
    }
}
