package io.vproxy.vfd.windows;

import java.io.IOException;
import java.nio.ByteBuffer;

public class WindowsNetworkFD extends WindowsFD {
    protected boolean connected = false;

    public WindowsNetworkFD(Windows windows) {
        super(windows);
    }

    public boolean isConnected() {
        return connected;
    }

    protected void checkConnected() throws IOException {
        if (!connected) {
            throw new IOException("not connected");
        }
    }

    public int read(ByteBuffer dst) throws IOException {
        checkFD();
        checkConnected();
        checkNotClosed();

        return 0; // TODO
    }

    public int write(ByteBuffer src) throws IOException {
        checkFD();
        checkConnected();
        checkNotClosed();

        return 0; // TODO
    }
}
