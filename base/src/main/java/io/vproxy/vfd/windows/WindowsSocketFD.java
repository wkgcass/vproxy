package io.vproxy.vfd.windows;

import io.vproxy.vfd.SocketFD;
import io.vproxy.vfd.posix.Posix;

import java.io.IOException;

public class WindowsSocketFD extends WindowsInetNetworkFD implements SocketFD {
    public WindowsSocketFD(Windows windows, Posix posix) {
        super(windows, posix);
    }

    public WindowsSocketFD(Windows windows, Posix posix, WinSocket socket, boolean ipv4) {
        this(windows, posix);
        setSocket(socket);
        this.ipv4 = ipv4;
        connected = true;
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
        clearWritable();
        return true;
    }
}
