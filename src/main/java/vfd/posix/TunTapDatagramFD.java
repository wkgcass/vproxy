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
    public final int flags;

    public TunTapDatagramFD(Posix posix, TunTapInfo tuntap, int flags) {
        super(posix);
        this.fd = tuntap.fd;
        this.connected = true;
        this.tuntap = tuntap;
        this.flags = flags;
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

    private String formatFlags() {
        StringBuilder sb = new StringBuilder();
        if ((flags & IFF_TUN) == IFF_TUN) {
            sb.append("|TUN");
        }
        if ((flags & IFF_TAP) == IFF_TAP) {
            sb.append("|TAP");
        }
        if ((flags & IFF_NO_PI) == IFF_NO_PI) {
            sb.append("|NO_PI");
        }
        if (sb.length() == 0) {
            return "";
        }
        return sb.delete(0, 1).toString();
    }

    @Override
    public String toString() {

        return "TunTapDatagramFD{" +
            "dev=" + tuntap.dev +
            ", fd=" + tuntap.fd +
            ", flags=" + formatFlags() +
            '}';
    }
}
