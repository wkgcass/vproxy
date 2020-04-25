package vfd.posix;

import vfd.DatagramFD;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

public class TunTapDatagramFD extends PosixNetworkFD implements DatagramFD {
    // make our own flags, the jni part will translate them into proper ones
    public static final int IFF_TUN = 0b001;
    public static final int IFF_TAP = 0b010;
    public static final int IFF_NO_PI = 0b100;

    public final TunTapInfo tuntap;

    public TunTapDatagramFD(Posix posix, TunTapInfo tuntap) {
        super(posix);
        this.fd = tuntap.fd;
        this.connected = true;
        this.tuntap = tuntap;
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
        return "TunTapDatagramFD{" +
            "tuntap=" + tuntap +
            '}';
    }
}
