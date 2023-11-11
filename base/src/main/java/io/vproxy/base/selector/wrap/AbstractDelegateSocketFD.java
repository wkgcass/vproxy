package io.vproxy.base.selector.wrap;

import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.SocketFD;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class AbstractDelegateSocketFD extends AbstractDelegateFD<SocketFD> implements SocketFD, VirtualFD, DelegatingTargetFD, DelegatingSourceFD {
    private void preCheck() throws IOException {
        checkError();
        if (getSourceFD() == null) {
            throw new IOException("not initialized yet");
        }
    }

    @Override
    public IPPort getLocalAddress() throws IOException {
        preCheck();
        return getSourceFD().getLocalAddress();
    }

    @Override
    public IPPort getRemoteAddress() throws IOException {
        preCheck();
        return getSourceFD().getRemoteAddress();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        preCheck();
        return getSourceFD().read(dst);
    }

    @Override
    public void connect(IPPort l4addr) throws IOException {
        preCheck();
        getSourceFD().connect(l4addr);
    }

    @Override
    public boolean isConnected() {
        return getSourceFD() != null && getSourceFD().isConnected();
    }

    @Override
    public void shutdownOutput() throws IOException {
        preCheck();
        getSourceFD().shutdownOutput();
    }

    @Override
    public boolean finishConnect() throws IOException {
        preCheck();
        return getSourceFD().finishConnect();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        preCheck();
        return getSourceFD().write(src);
    }
}
