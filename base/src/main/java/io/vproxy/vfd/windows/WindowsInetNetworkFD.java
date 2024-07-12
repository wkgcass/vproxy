package io.vproxy.vfd.windows;

import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.IPv6;
import io.vproxy.vfd.posix.Posix;

import java.io.IOException;

public abstract class WindowsInetNetworkFD extends WindowsNetworkFD {
    protected boolean ipv4 = false;

    protected WindowsInetNetworkFD(Windows windows, Posix posix) {
        super(windows, posix);
    }

    protected int createIPv4FD() throws IOException {
        return posix.createIPv4TcpFD();
    }

    protected int createIPv6FD() throws IOException {
        return posix.createIPv6TcpFD();
    }

    protected void doConnect(IPPort l4addr) throws IOException {
        windows.tcpConnect(socket, l4addr);
    }

    protected WinSocket wrapSocket(int fd) {
        return WinSocket.ofStream(fd);
    }

    public void connect(IPPort l4addr) throws IOException {
        checkNotClosed();
        if (socket != null) {
            throw new IOException("cannot call connect()");
        }
        if (l4addr.getAddress() instanceof IPv4) {
            ipv4 = true;

            int fd = createIPv4FD();
            setSocket(wrapSocket(fd));
            finishConfigAfterFDCreated();

            doConnect(l4addr);
        } else if (l4addr.getAddress() instanceof IPv6) {
            int fd = createIPv6FD();
            setSocket(wrapSocket(fd));
            finishConfigAfterFDCreated();

            doConnect(l4addr);
            ipv4 = false;
        } else {
            throw new IOException("unknown l3addr " + l4addr.getAddress());
        }
        // record the remote addr
        socket.remoteAddress = l4addr;
    }

    public IPPort getLocalAddress() throws IOException {
        checkFD();
        return socket.getLocalAddress(ipv4);
    }

    public IPPort getRemoteAddress() throws IOException {
        checkFD();
        return socket.getRemoteAddress(ipv4);
    }
}
