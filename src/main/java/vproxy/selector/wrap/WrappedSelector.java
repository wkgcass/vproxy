package vproxy.selector.wrap;

import vfd.*;
import vproxy.selector.wrap.udp.ServerDatagramFD;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.util.*;

public class WrappedSelector implements FDSelector {
    private final FDSelector selector;

    private static class REntry {
        EventSet watchedEvents;
        Object attachment;

        public REntry(EventSet watchedEvents, Object attachment) {
            this.watchedEvents = watchedEvents;
            this.attachment = attachment;
        }
    }

    private final Map<FD, REntry> virtualSocketFDs = new HashMap<>();
    private final Set<FD> readableFired = new HashSet<>();
    private final Set<FD> writableFired = new HashSet<>();

    public WrappedSelector(FDSelector selector) {
        this.selector = selector;
    }

    @Override
    public boolean isOpen() {
        return selector.isOpen();
    }

    private Set<SelectedEntry> calcVirtual() {
        Set<SelectedEntry> ret = new HashSet<>();
        for (Map.Entry<FD, REntry> e : virtualSocketFDs.entrySet()) {
            FD fd = e.getKey();
            REntry entry = e.getValue();

            boolean readable = false;
            boolean writable = false;
            if (entry.watchedEvents.have(Event.READABLE)) {
                if (readableFired.contains(fd)) {
                    readable = true;
                }
            }
            if (entry.watchedEvents.have(Event.WRITABLE)) {
                if (writableFired.contains(fd)) {
                    writable = true;
                }
            }
            EventSet eventSet;
            if (readable && writable) {
                eventSet = EventSet.readwrite();
            } else if (readable) {
                eventSet = EventSet.read();
            } else if (writable) {
                eventSet = EventSet.write();
            } else {
                eventSet = EventSet.none();
            }
            ret.add(new SelectedEntry(fd, eventSet, entry.attachment));
        }
        return ret;
    }

    @Override
    public Collection<SelectedEntry> select() throws IOException {
        var set = calcVirtual();
        if (set.isEmpty()) {
            return selector.select();
        } else {
            set.addAll(selector.selectNow());
            return set;
        }
    }

    @Override
    public Collection<SelectedEntry> selectNow() throws IOException {
        var set = calcVirtual();
        set.addAll(selector.selectNow());
        return set;
    }

    @Override
    public Collection<SelectedEntry> select(long millis) throws IOException {
        var set = calcVirtual();
        if (set.isEmpty()) {
            return selector.select(millis);
        } else {
            set.addAll(selector.selectNow());
            return set;
        }
    }

    @Override
    public void wakeup() {
        selector.wakeup();
    }

    @Override
    public boolean isRegistered(FD fd) {
        if (fd instanceof ServerDatagramFD.VirtualDatagramFD) {
            return virtualSocketFDs.containsKey(fd);
        } else {
            return selector.isRegistered(fd);
        }
    }

    @Override
    public void register(FD fd, EventSet ops, Object registerData) throws ClosedChannelException {
        if (fd instanceof ServerDatagramFD.VirtualDatagramFD) {
            virtualSocketFDs.put(fd, new REntry(ops, registerData));
            // check fire
            if (readableFired.contains(fd) || writableFired.contains(fd)) {
                wakeup();
            }
        } else {
            selector.register(fd, ops, registerData);
        }
    }

    @Override
    public void remove(FD fd) {
        if (fd instanceof ServerDatagramFD.VirtualDatagramFD) {
            virtualSocketFDs.remove(fd);
            readableFired.remove(fd);
            writableFired.remove(fd);
        } else {
            selector.remove(fd);
        }
    }

    @Override
    public void modify(FD fd, EventSet ops) {
        if (fd instanceof ServerDatagramFD.VirtualDatagramFD) {
            if (virtualSocketFDs.containsKey(fd)) {
                virtualSocketFDs.get(fd).watchedEvents = ops;
            } else {
                throw new CancelledKeyException();
            }
        } else {
            selector.modify(fd, ops);
        }
    }

    @Override
    public EventSet events(FD fd) {
        if (fd instanceof ServerDatagramFD.VirtualDatagramFD) {
            if (virtualSocketFDs.containsKey(fd)) {
                return virtualSocketFDs.get(fd).watchedEvents;
            } else {
                throw new CancelledKeyException();
            }
        } else {
            return selector.events(fd);
        }
    }

    @Override
    public Object attachment(FD fd) {
        if (fd instanceof ServerDatagramFD.VirtualDatagramFD) {
            if (virtualSocketFDs.containsKey(fd)) {
                return virtualSocketFDs.get(fd).attachment;
            } else {
                throw new CancelledKeyException();
            }
        } else {
            return selector.attachment(fd);
        }
    }

    @Override
    public Collection<RegisterEntry> entries() {
        var selectorRet = selector.entries();
        if (virtualSocketFDs.isEmpty()) {
            return selectorRet;
        }

        Set<RegisterEntry> ret = new HashSet<>(selectorRet);
        for (Map.Entry<FD, REntry> e : virtualSocketFDs.entrySet()) {
            var fd = e.getKey();
            var entry = e.getValue();
            ret.add(new RegisterEntry(fd, entry.watchedEvents, entry.attachment));
        }
        return ret;
    }

    @Override
    public void close() throws IOException {
        virtualSocketFDs.clear();
        readableFired.clear();
        writableFired.clear();
        selector.close();
    }

    public void registerVirtualReadable(FD fd) {
        if (!selector.isOpen()) {
            throw new ClosedSelectorException();
        }
        readableFired.add(fd);

        // check fired
        var rentry = virtualSocketFDs.get(fd);
        if (rentry == null) {
            return;
        }
        if (rentry.watchedEvents.have(Event.READABLE)) {
            wakeup();
        }
    }

    public void removeVirtualReadable(FD fd) {
        readableFired.remove(fd);
    }

    public void registerVirtualWritable(FD fd) {
        if (!selector.isOpen()) {
            throw new ClosedSelectorException();
        }
        writableFired.add(fd);

        // check fired
        var rentry = virtualSocketFDs.get(fd);
        if (rentry == null) {
            return;
        }
        if (rentry.watchedEvents.have(Event.WRITABLE)) {
            wakeup();
        }
    }

    public void removeVirtualWritable(FD fd) {
        writableFired.remove(fd);
    }
}
