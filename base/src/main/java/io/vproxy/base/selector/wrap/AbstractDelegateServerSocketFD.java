package io.vproxy.base.selector.wrap;

import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.ServerSocketFD;
import io.vproxy.vfd.SocketFD;

import java.io.IOException;

public class AbstractDelegateServerSocketFD extends AbstractDelegateFD<ServerSocketFD> implements ServerSocketFD, VirtualFD, DelegatingTargetFD, DelegatingSourceFD {
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
    public SocketFD accept() throws IOException {
        preCheck();
        return getSourceFD().accept();
    }

    @Override
    public void bind(IPPort l4addr) throws IOException {
        preCheck();
        getSourceFD().bind(l4addr);
    }
}
