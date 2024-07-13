package io.vproxy.vfd.windows;

import io.vproxy.vfd.SocketFD;
import io.vproxy.vfd.posix.Posix;

import java.io.IOException;

public class WindowsSocketFD extends WindowsInetNetworkFD implements SocketFD {
    protected WindowsSocketFD(Windows windows, Posix posix) {
        super(windows, posix);
    }

    // accepted socket
    protected WindowsSocketFD(Windows windows, Posix posix, WinSocket socket, boolean ipv4) {
        this(windows, posix);
        setSocket(socket);
        this.ipv4 = ipv4;
        connected = true;

        deliverStreamSocketReadOperation();
        setWritable();
    }

    @Override
    public void shutdownOutput() throws IOException {
        checkFD();
        checkConnected();
        checkNotClosed();
        windows.wsaSendDisconnect(socket);
    }

    @Override
    public boolean finishConnect() throws IOException {
        checkFD();
        checkNotClosed();
        if (!canBeConnected) {
            throw new IOException("not connected yet");
        }
        connected = true;
        deliverStreamSocketReadOperation();
        return true;
    }
}
