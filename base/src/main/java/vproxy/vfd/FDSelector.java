package vproxy.vfd;

import vproxy.base.util.objectpool.GarbageFree;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.Collection;

public interface FDSelector extends Closeable {
    boolean isOpen();

    @GarbageFree
    Collection<SelectedEntry> select() throws IOException;

    @GarbageFree
    Collection<SelectedEntry> selectNow() throws IOException;

    @GarbageFree
    Collection<SelectedEntry> select(long millis) throws IOException;

    boolean supportsWakeup();

    void wakeup();

    boolean isRegistered(FD fd);

    void register(FD fd, EventSet ops, Object registerData) throws ClosedChannelException;

    void remove(FD fd);

    void modify(FD fd, EventSet ops);

    EventSet events(FD fd);

    Object attachment(FD fd);

    Collection<RegisterEntry> entries();
}
