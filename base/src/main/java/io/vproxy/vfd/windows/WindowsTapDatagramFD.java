package io.vproxy.vfd.windows;

import io.vproxy.vfd.NoSockAddr;
import io.vproxy.vfd.TapDatagramFD;
import io.vproxy.vfd.TapInfo;

import java.io.IOException;
import java.nio.ByteBuffer;

public class WindowsTapDatagramFD extends WindowsNetworkFD implements TapDatagramFD {
    public final TapInfo tap;

    public WindowsTapDatagramFD(Windows windows, WinSocket socket, TapInfo tap) {
        super(windows);
        setSocket(socket);
        this.connected = true;
        this.tap = tap;
    }

    @Override
    public TapInfo getTap() {
        return tap;
    }

    @Override
    public boolean isTun() {
        return false;
    }

    @Override
    public void connect(NoSockAddr l4addr) throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    @Override
    public void bind(NoSockAddr l4addr) throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    @Override
    public int send(ByteBuffer buf, NoSockAddr remote) throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    @Override
    public NoSockAddr receive(ByteBuffer buf) throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    @Override
    public NoSockAddr getLocalAddress() throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    @Override
    public NoSockAddr getRemoteAddress() throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }
}
