package vfd.posix;

import vfd.*;

import java.io.IOException;
import java.nio.ByteBuffer;

public class PosixDatagramFD extends PosixInetNetworkFD implements DatagramFD {
    private boolean bond = false;

    public PosixDatagramFD(Posix posix) {
        super(posix);
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
    public void connect(IPPort l4addr) throws IOException {
        super.connect(l4addr);
        connected = true;
    }

    @Override
    public void bind(IPPort l4addr) throws IOException {
        checkNotClosed();
        if (connected) {
            throw new IOException("is already connected");
        }
        if (bond) {
            throw new IOException("is already bond");
        }
        int port = l4addr.getPort();
        if (l4addr.getAddress() instanceof IPv4) {
            fd = posix.createIPv4UdpFD();
            finishConfigAfterFDCreated();
            ipv4 = true;
            int ipv4 = IP.ipv4Bytes2Int(l4addr.getAddress().getAddress());
            posix.bindIPv4(fd, ipv4, port);
        } else if (l4addr.getAddress() instanceof IPv6) {
            fd = posix.createIPv6UdpFD();
            finishConfigAfterFDCreated();
            ipv4 = false;
            String ipv6 = l4addr.getAddress().formatToIPString();
            posix.bindIPv6(fd, ipv6, port);
        } else {
            throw new IOException("unknown l3addr " + l4addr.getAddress());
        }
        bond = true;
    }

    @Override
    public int send(ByteBuffer buf, IPPort remote) throws IOException {
        if (connected) {
            throw new IOException("this fd is already connected");
        }
        checkNotClosed();
        if (fd == -1) {
            if (remote.getAddress() instanceof IPv4) {
                fd = createIPv4FD();
                ipv4 = true;
            } else {
                fd = createIPv6FD();
                ipv4 = false;
            }
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
        int off = 0;
        int len = buf.limit() - buf.position();
        boolean needCopy = false;
        ByteBuffer directBuffer;
        if (buf.isDirect()) {
            directBuffer = buf;
            off = buf.position();
        } else {
            directBuffer = getDirectBufferForWriting(len).realBuffer();
            directBuffer.put(buf);
            needCopy = true;
        }
        int n = 0;
        try {
            int port = remote.getPort();
            if (ipv4) {
                int ip = IP.ipv4Bytes2Int(remote.getAddress().getAddress());
                n = posix.sendtoIPv4(fd, directBuffer, off, len, ip, port);
            } else {
                String ip = remote.getAddress().formatToIPString();
                n = posix.sendtoIPv6(fd, directBuffer, off, len, ip, port);
            }
        } finally {
            if (needCopy) { // src was fully read
                if (n < len) {
                    buf.position(buf.limit() - len + n);
                }
            } else { // src was not modified
                if (n > 0) {
                    buf.position(buf.position() + n);
                }
            }
            resetDirectBufferForWriting();
        }
        return n;
    }

    @Override
    public IPPort receive(ByteBuffer buf) throws IOException {
        checkFD();
        checkNotClosed();
        if (!bond) {
            throw new IOException("not bond");
        }
        int off = 0;
        int len = buf.limit() - buf.position();
        boolean needCopy = false;
        ByteBuffer directBuffer;
        if (buf.isDirect()) {
            directBuffer = buf;
            off = buf.position();
        } else {
            directBuffer = getDirectBufferForReading(len).realBuffer();
            needCopy = true;
        }
        VSocketAddress l4addr;
        int n = 0;
        try {
            UDPRecvResult tup;
            if (ipv4) {
                tup = posix.recvfromIPv4(fd, directBuffer, off, len);
            } else {
                tup = posix.recvfromIPv6(fd, directBuffer, off, len);
            }
            if (tup == null) { // nothing received
                return null;
            }
            l4addr = tup.address;
            n = tup.len;
        } finally {
            if (n > 0) {
                if (needCopy) {
                    directBuffer.limit(n).position(0);
                    buf.put(directBuffer);
                } else {
                    buf.position(buf.position() + n);
                }
            }
            resetDirectBufferForReading();
        }
        return l4addr.toIPPort();
    }
}
