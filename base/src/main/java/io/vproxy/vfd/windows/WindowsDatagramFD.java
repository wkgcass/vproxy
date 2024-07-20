package io.vproxy.vfd.windows;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.vfd.*;
import io.vproxy.vfd.posix.Posix;

import java.io.IOException;
import java.nio.ByteBuffer;

public class WindowsDatagramFD extends WindowsInetNetworkFD implements DatagramFD {
    private boolean bond = false;
    private IPPort lastReceivedPacketIPPort = null;

    protected WindowsDatagramFD(Windows windows, Posix posix) {
        super(windows, posix);
        setWritable(); // datagram fds are always considered writable
    }

    @Override
    protected int createIPv4FD() throws IOException {
        return posix.createIPv4UdpFD();
    }

    @Override
    protected int createIPv6FD() throws IOException {
        return posix.createIPv6UdpFD();
    }

    @Override
    protected void doRecv() throws IOException {
        if (bond) {
            windows.wsaRecvFrom(socket);
        } else {
            super.doRecv();
        }
    }

    @Override
    protected void doConnect(IPPort l4addr) throws IOException {
        if (ipv4) {
            int ipv4 = IP.ipv4Bytes2Int(l4addr.getAddress().getAddress());
            posix.connectIPv4((int) socket.fd.MEMORY.address(), ipv4, l4addr.getPort());
        } else {
            var ipv6 = ((IPv6) l4addr.getAddress()).formatToIPStringWithoutBrackets();
            posix.connectIPv6((int) socket.fd.MEMORY.address(), ipv6, l4addr.getPort());
        }
    }

    @Override
    protected void onReadComplete() {
        if (!bond) {
            return;
        }
        IPPort ipport;
        try {
            ipport = windows.convertAddress(socket, ipv4);
        } catch (IOException e) {
            Logger.fatal(LogType.SYS_ERROR, "failed to get received packet's address from " + socket);
            lastReceivedPacketIPPort = new IPPort("0.0.0.0:0");
            return;
        }
        lastReceivedPacketIPPort = ipport;
    }

    @Override
    public void connect(IPPort l4addr) throws IOException {
        super.connect(l4addr);
        connected = true;
        deliverReadOperation();
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

            int fd = createIPv4FD();
            setSocket(WinSocket.ofDatagram(fd));
            finishConfigAfterFDCreated();
            int ipv4 = IP.ipv4Bytes2Int(l4addr.getAddress().getAddress());
            posix.bindIPv4(fd, ipv4, port);
        } else if (l4addr.getAddress() instanceof IPv6) {
            int fd = createIPv6FD();
            setSocket(WinSocket.ofDatagram(fd));
            finishConfigAfterFDCreated();
            String ipv6 = ((IPv6) l4addr.getAddress()).formatToIPStringWithoutBrackets();
            posix.bindIPv6(fd, ipv6, port);
        } else {
            throw new IOException("unknown l3addr " + l4addr.getAddress());
        }

        socket.localAddress = l4addr;
        bond = true;
        deliverReadOperation();
    }

    @Override
    public int send(ByteBuffer buf, IPPort remote) throws IOException {
        if (connected) {
            throw new IOException("this fd is already connected");
        }
        checkNotClosed();
        if (socket == null) {
            int fd;
            if (remote.getAddress() instanceof IPv4) {
                ipv4 = true;
                fd = createIPv4FD();
            } else {
                fd = createIPv6FD();
            }
            setSocket(WinSocket.ofDatagram(fd));
            finishConfigAfterFDCreated();
        }
        if (ipv4) {
            if (!(remote.getAddress() instanceof IPv4)) {
                throw new IOException("unsupported address for this fd: " + remote);
            }
        } else {
            if (!(remote.getAddress() instanceof IPv6)) {
                throw new IOException("unsupported address for this fd: " + remote);
            }
        }

        int len = buf.limit() - buf.position();

        var ctx = IOCPUtils.buildContextForSendingDatagramPacket(socket, buf);
        windows.wsaSendTo(socket, ctx, remote);

        return len;
    }

    @Override
    public IPPort receive(ByteBuffer buf) throws IOException {
        checkFD();
        checkNotClosed();
        if (!bond) {
            throw new IOException("not bond");
        }
        var lastReceivedPacketIPPort = this.lastReceivedPacketIPPort;
        if (lastReceivedPacketIPPort == null) {
            // nothing received
            return null;
        }
        var len = buf.limit() - buf.position();
        if (len < socket.recvRingBuffer.used()) {
            assert Logger.lowLevelDebug("user provided buf is too small");
            return null;
        }
        this.lastReceivedPacketIPPort = null;

        socket.recvRingBuffer.writeTo(buf);
        clearReadable();
        deliverReadOperation();
        return lastReceivedPacketIPPort;
    }
}
