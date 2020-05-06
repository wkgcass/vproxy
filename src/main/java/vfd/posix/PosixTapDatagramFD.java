package vfd.posix;

import vfd.TapDatagramFD;
import vfd.TapInfo;
import vproxy.util.OS;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

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

    @Override
    public void close() throws IOException {
        if (isOpen()) {
            if (OS.isMac()) { // hack on mac
                // the kernel extension will block forever if the tap is not assigned with any ip
                // so assign one for it to make sure it doesn't reach that condition
                // BSD will definitely have /sbin/ifconfig, so it's safe to call
                new Thread(() -> {
                    ProcessBuilder pb = new ProcessBuilder()
                        .command("/sbin/ifconfig", tap.dev, "0.0.0.1/32");
                    try {
                        var p = pb.start();
                        p.waitFor(1, TimeUnit.SECONDS);
                        p.destroyForcibly();
                    } catch (Throwable ignore) {
                    }
                }).start();
            }
        }
        super.close();
    }
}
