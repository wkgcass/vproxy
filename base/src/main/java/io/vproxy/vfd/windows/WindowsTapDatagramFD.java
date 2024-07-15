package io.vproxy.vfd.windows;

import io.vproxy.vfd.NoSockAddr;
import io.vproxy.vfd.TapDatagramFD;
import io.vproxy.vfd.TapInfo;
import io.vproxy.vfd.posix.Posix;

import java.io.IOException;
import java.nio.ByteBuffer;

public class WindowsTapDatagramFD extends WindowsNetworkFD implements TapDatagramFD {
    public final TapInfo tap;

    public WindowsTapDatagramFD(Windows windows, Posix posix, WinSocket socket, TapInfo tap) throws IOException {
        super(windows, posix);
        setSocket(socket);
        this.connected = true;
        this.tap = tap;
        setWritable(); // datagram fds are always considered writable
        deliverReadOperation();
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

    @Override
    protected void doRecv() throws IOException {
        windows.readFile(socket);
    }

    @Override
    protected void doSend() throws IOException {
        windows.writeFile(socket);
    }

    @Override
    protected void doSend(VIOContext ctx) throws IOException {
        windows.writeFile(socket, ctx);
    }
}
