package io.vproxy.vfd.windows;

import io.vproxy.vfd.*;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.Collection;
import java.util.List;

public class IOCPSelector implements FDSelector {
    public final WinIOCP iocp;

    public IOCPSelector(WinIOCP iocp) {
        this.iocp = iocp;
    }

    @Override
    public boolean isOpen() {
        return !iocp.isClosed();
    }

    @Override
    public Collection<SelectedEntry> select() throws IOException {
        return List.of(); // TODO
    }

    @Override
    public Collection<SelectedEntry> selectNow() throws IOException {
        return List.of(); // TODO
    }

    @Override
    public Collection<SelectedEntry> select(long millis) throws IOException {
        return List.of(); // TODO
    }

    @Override
    public void wakeup() {
        IOCPUtils.notify(iocp.handle);
    }

    @Override
    public boolean isRegistered(FD fd) {
        return false; // TODO
    }

    @Override
    public void register(FD fd, EventSet ops, Object registerData) throws ClosedChannelException {
        // TODO
    }

    @Override
    public void remove(FD fd) {
        // TODO
    }

    @Override
    public void modify(FD fd, EventSet ops) {
        // TODO
    }

    @Override
    public EventSet events(FD fd) {
        return null; // TODO
    }

    @Override
    public Object attachment(FD fd) {
        return null; // TODO
    }

    @Override
    public Collection<RegisterEntry> entries() {
        return List.of(); // TODO
    }

    @Override
    public void close() throws IOException {
        iocp.close();
    }
}
