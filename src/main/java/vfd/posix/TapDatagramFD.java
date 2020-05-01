package vfd.posix;

import vfd.DatagramFD;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

public class TapDatagramFD extends PosixNetworkFD implements DatagramFD {
    public final TapInfo tap;

    public TapDatagramFD(Posix posix, TapInfo tap) {
        super(posix);
        this.fd = tap.fd;
        this.connected = true;
        this.tap = tap;
    }

    @Override
    public void bind(InetSocketAddress l4addr) throws IOException {
        throw new IOException(new UnsupportedOperationException("tun-tap dev"));
    }

    @Override
    public int send(ByteBuffer buf, InetSocketAddress remote) throws IOException {
        throw new IOException(new UnsupportedOperationException("tun-tap dev"));
    }

    @Override
    public SocketAddress receive(ByteBuffer buf) throws IOException {
        throw new IOException(new UnsupportedOperationException("tun-tap dev"));
    }

    @Override
    public String toString() {
        return "TapDatagramFD{" +
            "dev=" + tap.dev +
            ", fd=" + tap.fd +
            '}';
    }
}
