package vfd.posix;

import vfd.NoSockAddr;
import vfd.TapDatagramFD;
import vfd.TapInfo;
import vfd.type.FDCloseReq;
import vproxybase.util.OS;

import java.io.IOException;
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
    public void connect(NoSockAddr l4addr) throws IOException {
        throw new IOException(new UnsupportedOperationException("tun-tap dev"));
    }

    @Override
    public void bind(NoSockAddr l4addr) throws IOException {
        throw new IOException(new UnsupportedOperationException("tun-tap dev"));
    }

    @Override
    public int send(ByteBuffer buf, NoSockAddr remote) throws IOException {
        throw new IOException(new UnsupportedOperationException("tun-tap dev"));
    }

    @Override
    public NoSockAddr receive(ByteBuffer buf) throws IOException {
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
    public PosixFDCloseReturn close(FDCloseReq req) throws IOException {
        if (isOpen()) {
            if (OS.isMac()) { // hack on mac
                // the kernel extension will block forever if the tap is not assigned with any ip
                // so assign one for it to make sure it doesn't reach that condition
                // BSD will definitely have /sbin/ifconfig, so it's safe to call
                ProcessBuilder pb = new ProcessBuilder()
                    .command("/sbin/ifconfig", tap.dev, "0.0.0.1/32");
                try {
                    var p = pb.start();
                    p.waitFor(1, TimeUnit.SECONDS);
                    p.destroyForcibly();
                } catch (Throwable ignore) {
                }
            }
        }
        return super.close(req);
    }

    @Override
    public NoSockAddr getLocalAddress() throws IOException {
        throw new IOException(new UnsupportedOperationException("tun-tap dev"));
    }

    @Override
    public NoSockAddr getRemoteAddress() throws IOException {
        throw new IOException(new UnsupportedOperationException("tun-tap dev"));
    }
}
