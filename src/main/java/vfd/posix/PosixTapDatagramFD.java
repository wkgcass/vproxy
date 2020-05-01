package vfd.posix;

import vfd.TapDatagramFD;
import vfd.TapInfo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

public class PosixTapDatagramFD extends PosixNetworkFD implements TapDatagramFD {
    public final TapInfo tap;

    public PosixTapDatagramFD(Posix posix, TapInfo tap) {
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

    @Override
    public TapInfo getTap() {
        return tap;
    }
}
