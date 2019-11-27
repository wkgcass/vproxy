package vfd.jdk;

import vfd.*;
import vproxy.util.Logger;

import java.io.IOException;
import java.nio.channels.*;
import java.util.*;

public class ChannelSelector implements FDSelector {
    private static class Att {
        final FD fd;
        final Object att;

        private Att(FD fd, Object att) {
            this.fd = fd;
            this.att = att;
        }
    }

    private final Selector selector;

    public ChannelSelector(Selector selector) {
        this.selector = selector;
    }

    @Override
    public boolean isOpen() {
        return selector.isOpen();
    }

    private Collection<SelectedEntry> getSelectionEntries(int cnt) {
        if (cnt == 0) return Collections.emptySet();

        Set<SelectedEntry> ret = new HashSet<>(cnt);

        Set<SelectionKey> set = selector.selectedKeys();
        Iterator<SelectionKey> keys = set.iterator();
        while (keys.hasNext()) {
            SelectionKey key = keys.next();
            keys.remove();

            if (!key.isValid()) {
                continue;
            }

            Att att = (Att) key.attachment();
            ret.add(new SelectedEntry(att.fd, events(key.readyOps()), att.att));
        }
        return ret;
    }

    @Override
    public Collection<SelectedEntry> select() throws IOException {
        int n = selector.select();
        return getSelectionEntries(n);
    }

    @Override
    public Collection<SelectedEntry> selectNow() throws IOException {
        int n = selector.selectNow();
        return getSelectionEntries(n);
    }

    @Override
    public Collection<SelectedEntry> select(long millis) throws IOException {
        int n = selector.select(millis);
        return getSelectionEntries(n);
    }

    @Override
    public boolean supportsWakeup() {
        return true;
    }

    @Override
    public void wakeup() {
        selector.wakeup();
    }

    @Override
    public boolean isRegistered(FD fd) {
        return ((ChannelFD) fd.real()).getChannel().keyFor(selector) != null;
    }

    private int buildInterestOps(FD fd, EventSet ops) {
        fd = fd.real();
        int op = 0;
        if (fd instanceof ServerSocketFD) {
            if (ops.have(Event.READABLE)) {
                op |= SelectionKey.OP_ACCEPT;
            }
        } else if (fd instanceof SocketFD) {
            if (ops.have(Event.READABLE)) {
                op |= SelectionKey.OP_READ;
            }
            if (ops.have(Event.WRITABLE)) {
                op |= (SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT);
            }
        } else if (fd instanceof DatagramFD) {
            if (ops.have(Event.READABLE)) {
                op |= SelectionKey.OP_READ;
            }
            if (ops.have(Event.WRITABLE)) {
                op |= SelectionKey.OP_WRITE;
            }
        } else {
            throw new IllegalArgumentException("unknown fd type: " + fd);
        }
        return op;
    }

    @Override
    public void register(FD fd, EventSet ops, Object registerData) throws ClosedChannelException {
        int iOps = buildInterestOps(fd, ops);
        //noinspection MagicConstant
        ((ChannelFD) fd.real()).getChannel().register(selector, iOps, new Att(fd, registerData));
    }

    @Override
    public void remove(FD fd) {
        SelectableChannel channel = ((ChannelFD) fd.real()).getChannel();

        SelectionKey key;
        // synchronize the channel
        // to prevent it being canceled from multiple threads
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (channel) {
            key = channel.keyFor(selector);
            if (key == null)
                return;
        }
        key.cancel();
    }

    private SelectionKey getKeyCheckNull(FD fd) {
        SelectableChannel channel = ((ChannelFD) fd.real()).getChannel();

        SelectionKey key = channel.keyFor(selector);
        if (key == null)
            throw new IllegalArgumentException("channel is not registered with this selector");
        return key;
    }

    @Override
    public void modify(FD fd, EventSet ops) {
        SelectionKey key = getKeyCheckNull(fd);
        int iOps = buildInterestOps(fd, ops);
        //noinspection MagicConstant
        key.interestOps(iOps);
    }

    @Override
    public EventSet events(FD fd) {
        SelectionKey key = getKeyCheckNull(fd);

        return events(key.interestOps());
    }

    private EventSet events(int ops) {
        boolean readable = false;
        boolean writable = false;

        if ((ops & (SelectionKey.OP_ACCEPT | SelectionKey.OP_READ)) != 0) {
            readable = true;
        }
        if ((ops & (SelectionKey.OP_CONNECT | SelectionKey.OP_WRITE)) != 0) {
            writable = true;
        }
        if (readable && writable) {
            return EventSet.readwrite();
        } else if (readable) {
            return EventSet.read();
        } else if (writable) {
            return EventSet.write();
        } else {
            return EventSet.none();
        }
    }

    @Override
    public Object attachment(FD fd) {
        SelectionKey key = getKeyCheckNull(fd);

        Att att = (Att) key.attachment();
        return att.att;
    }

    @Override
    public Collection<RegisterEntry> entries() {
        var keys = selector.keys();
        Set<RegisterEntry> entries = new HashSet<>(keys.size());
        for (SelectionKey k : keys) {
            Att att = (Att) k.attachment();
            EventSet events = null;
            try {
                events = events(k.interestOps());
            } catch (CancelledKeyException e) {
                assert Logger.lowLevelDebug("failed to retrieve interestOps for " + k.channel() + " when calling entries()");
            }
            entries.add(new RegisterEntry(att.fd, events, att.att));
        }
        return entries;
    }

    @Override
    public void close() throws IOException {
        selector.close();
    }
}
