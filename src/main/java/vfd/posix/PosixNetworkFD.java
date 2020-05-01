package vfd.posix;

import vproxy.util.Utils;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

public class PosixNetworkFD extends PosixFD {
    protected boolean connected = false;
    protected boolean ipv4 = false;

    private InetSocketAddress local;
    private InetSocketAddress remote;

    protected PosixNetworkFD(Posix posix) {
        super(posix);
    }

    protected int createIPv4FD() throws IOException {
        return posix.createIPv4TcpFD();
    }

    protected int createIPv6FD() throws IOException {
        return posix.createIPv6TcpFD();
    }

    public void connect(InetSocketAddress l4addr) throws IOException {
        checkNotClosed();
        if (fd != -1) {
            throw new IOException("cannot call connect()");
        }
        if (l4addr.getAddress() instanceof Inet4Address) {
            byte[] host = l4addr.getAddress().getAddress();
            assert host.length == 4;

            fd = createIPv4FD();
            finishConfigAfterFDCreated();
            int n = Utils.ipv4Bytes2Int(host);
            posix.connectIPv4(fd, n, l4addr.getPort());
            ipv4 = true;
        } else if (l4addr.getAddress() instanceof Inet6Address) {
            fd = createIPv6FD();
            finishConfigAfterFDCreated();
            String str = Utils.ipStr(l4addr.getAddress().getAddress());
            posix.connectIPv6(fd, str, l4addr.getPort());
            ipv4 = false;
        } else {
            throw new IOException("unknown l3addr " + l4addr.getAddress());
        }
        // record the remote addr
        remote = l4addr;
    }

    public boolean isConnected() {
        return connected;
    }

    protected void checkConnected() throws IOException {
        if (!connected) {
            throw new IOException("not connected");
        }
    }

    public SocketAddress getLocalAddress() throws IOException {
        checkFD();
        if (local == null) {
            checkNotClosed();
            if (ipv4) {
                local = posix.getIPv4Local(fd).toInetSocketAddress();
            } else {
                local = posix.getIPv6Local(fd).toInetSocketAddress();
            }
        }
        return local;
    }

    public SocketAddress getRemoteAddress() throws IOException {
        checkFD();
        if (remote == null) {
            checkNotClosed();
            if (ipv4) {
                remote = posix.getIPv4Remote(fd).toInetSocketAddress();
            } else {
                remote = posix.getIPv6Remote(fd).toInetSocketAddress();
            }
        }
        return remote;
    }

    public int read(ByteBuffer dst) throws IOException {
        checkFD();
        checkConnected();
        checkNotClosed();

        return utilRead(dst, (buf, off, len) -> posix.read(fd, buf, off, len));
    }

    public int write(ByteBuffer src) throws IOException {
        checkFD();
        checkConnected();
        checkNotClosed();

        return utilWrite(src, (buf, off, len) -> posix.write(fd, buf, off, len));
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
            "fd=" + fd +
            ", closed=" + !isOpen() +
            ", local=" + local +
            ", remote=" + remote +
            '}';
    }
}
