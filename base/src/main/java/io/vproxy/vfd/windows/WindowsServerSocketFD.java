package io.vproxy.vfd.windows;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.coll.RingQueue;
import io.vproxy.vfd.*;
import io.vproxy.vfd.posix.Posix;

import java.io.IOException;

public class WindowsServerSocketFD extends WindowsFD implements ServerSocketFD {
    private boolean ipv4 = false;
    private final RingQueue<IOException> errors = new RingQueue<>();
    private final RingQueue<WindowsSocketFD> acceptQueue = new RingQueue<>();

    protected WindowsServerSocketFD(Windows windows, Posix posix) {
        super(windows, posix);
    }

    @Override
    public IPPort getLocalAddress() throws IOException {
        checkFD();
        return socket.getLocalAddress(ipv4);
    }

    @Override
    public SocketFD accept() throws IOException {
        checkFD();
        checkNotClosed();
        checkError();

        var fd = acceptQueue.poll();
        if (fd == null) {
            return null;
        }
        if (acceptQueue.isEmpty()) {
            clearReadable();
        }
        return fd;
    }

    private void checkError() throws IOException {
        var e = errors.poll();
        if (e != null) {
            throw e;
        }
    }

    private void pushError(IOException error) {
        errors.add(error);
    }

    @Override
    public void bind(IPPort l4addr) throws IOException {
        checkNotClosed();
        if (socket != null) {
            throw new IOException("already bond " + socket.getLocalAddress(ipv4));
        }
        int port = l4addr.getPort();
        if (l4addr.getAddress() instanceof IPv4) {
            ipv4 = true;

            int fd = posix.createIPv4TcpFD();
            setSocket(WinSocket.ofServer(fd));
            finishConfigAfterFDCreated();
            int ipv4 = IP.ipv4Bytes2Int(l4addr.getAddress().getAddress());
            posix.bindIPv4(fd, ipv4, port);
        } else if (l4addr.getAddress() instanceof IPv6) {
            int fd = posix.createIPv6TcpFD();
            setSocket(WinSocket.ofServer(fd));
            finishConfigAfterFDCreated();
            String ipv6 = ((IPv6) l4addr.getAddress()).formatToIPStringWithoutBrackets();
            posix.bindIPv6(fd, ipv6, port);
        } else {
            throw new IOException("unknown l3addr " + l4addr.getAddress());
        }

        deliverAccept();

        socket.localAddress = l4addr;
    }

    private void deliverAccept() {
        int fd;
        try {
            if (ipv4) {
                fd = posix.createIPv4TcpFD();
            } else {
                fd = posix.createIPv6TcpFD();
            }
        } catch (IOException e) {
            Logger.fatal(LogType.SYS_ERROR, "failed to create " + (ipv4 ? "IPv4" : "IPv6") + " tcp fd", e);
            pushError(e);
            return;
        }
        var newSocket = WinSocket.ofAcceptedStream(fd, socket);
        newSocket.ud = this; // will call ioComplete method defined here
        try {
            windows.acceptEx(newSocket);
        } catch (IOException e) {
            Logger.fatal(LogType.SYS_ERROR, "failed to deliver acceptEx", e);
            pushError(e);
        }
    }

    @Override
    protected void ioComplete(VIOContext ctx, int nbytes) {
        if (ctx.getIoType() != IOType.ACCEPT.code) {
            Logger.shouldNotHappen("WindowsServerSocketFD only expects ACCEPT event, but got " + ctx.getIoType());
            return;
        }

        var newSocket = (WinSocket) ctx.getRef().getRef();

        var fd = new WindowsSocketFD(windows, posix, newSocket, ipv4);

        acceptQueue.add(fd);
        setReadable();

        deliverAccept();
    }
}
