package vproxybase.selector.wrap;

import vfd.*;
import vproxybase.util.Lock;
import vproxybase.util.LogType;
import vproxybase.util.Logger;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
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

        @Override
        public String toString() {
            return "REntry{" +
                "watchedEvents=" + watchedEvents +
                ", attachment=" + attachment +
                '}';
        }
    }

    private final Lock VIRTUAL_LOCK; // only lock when calculating and registering, which would be enough for current code base
    private final Map<VirtualFD, REntry> virtualSocketFDs = new HashMap<>();
    private final Set<FD> readableFired = new HashSet<>();
    private final Set<FD> writableFired = new HashSet<>();
    private final Lock SELECTOR_OPERATION_LOCK = Lock.create();

    public WrappedSelector(FDSelector selector) {
        this.selector = selector;
        if (VFDConfig.useFStack) {
            VIRTUAL_LOCK = Lock.createMock();
        } else {
            VIRTUAL_LOCK = Lock.create();
        }
    }

    @Override
    public boolean isOpen() {
        return selector.isOpen();
    }

    private Set<SelectedEntry> calcVirtual() {
        Set<SelectedEntry> ret = new HashSet<>();
        //noinspection unused
        try (var unused = VIRTUAL_LOCK.lock()) {
            for (Map.Entry<VirtualFD, REntry> e : virtualSocketFDs.entrySet()) {
                FD fd = e.getKey();
                REntry entry = e.getValue();

                boolean readable = false;
                boolean writable = false;
                if (entry.watchedEvents.have(Event.READABLE)) {
                    if (readableFired.contains(fd)) {
                        assert Logger.lowLevelDebug("fire readable for " + fd);
                        readable = true;
                    }
                }
                if (entry.watchedEvents.have(Event.WRITABLE)) {
                    if (writableFired.contains(fd)) {
                        assert Logger.lowLevelDebug("fire writable for " + fd);
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
                    eventSet = null;
                }
                if (eventSet != null) {
                    ret.add(new SelectedEntry(fd, eventSet, entry.attachment));
                }
            }
        }
        return ret;
    }

    private Collection<SelectedEntry> handleRealSelect(Collection<SelectedEntry> entries) {
        for (SelectedEntry entry : entries) {
            if (entry.fd instanceof WritableAware) {
                if (entry.ready.have(Event.WRITABLE)) {
                    ((WritableAware) entry.fd).writable();
                }
            }
        }
        return entries;
    }

    @Override
    public Collection<SelectedEntry> select() throws IOException {
        var set = calcVirtual();
        if (set.isEmpty()) {
            return handleRealSelect(selector.select());
        } else {
            set.addAll(handleRealSelect(selector.selectNow()));
            return set;
        }
    }

    @Override
    public Collection<SelectedEntry> selectNow() throws IOException {
        var set = calcVirtual();
        set.addAll(handleRealSelect(selector.selectNow()));
        return set;
    }

    @Override
    public Collection<SelectedEntry> select(long millis) throws IOException {
        var set = calcVirtual();
        if (set.isEmpty()) {
            return handleRealSelect(selector.select(millis));
        } else {
            set.addAll(handleRealSelect(selector.selectNow()));
            return set;
        }
    }

    @Override
    public boolean supportsWakeup() {
        return selector.supportsWakeup();
    }

    @Override
    public void wakeup() {
        if (selector.supportsWakeup()) {
            //noinspection unused
            try (var unused = SELECTOR_OPERATION_LOCK.lock()) {
                selector.wakeup();
            }
        }
    }

    @Override
    public boolean isRegistered(FD fd) {
        if (fd instanceof VirtualFD) {
            return virtualSocketFDs.containsKey(fd);
        } else {
            return selector.isRegistered(fd);
        }
    }

    @Override
    public void register(FD fd, EventSet ops, Object registerData) throws ClosedChannelException {
        assert Logger.lowLevelDebug("register fd to selector " + fd);
        if (fd instanceof VirtualFD) {
            //noinspection unused
            try (var unused = VIRTUAL_LOCK.lock()) {
                virtualSocketFDs.put((VirtualFD) fd, new REntry(ops, registerData));
            }
            // check fire
            if (readableFired.contains(fd) || writableFired.contains(fd)) {
                wakeup();
            }
            ((VirtualFD) fd).onRegister();
        } else {
            if (fd instanceof WritableAware) {
                ops = ops.combine(EventSet.write());
            }

            selector.register(fd, ops, registerData);
        }
    }

    @Override
    public void remove(FD fd) {
        assert Logger.lowLevelDebug("remove fd from selector " + fd);
        if (fd instanceof VirtualFD) {
            virtualSocketFDs.remove(fd);
            readableFired.remove(fd);
            writableFired.remove(fd);
            ((VirtualFD) fd).onRemove();
        } else {
            selector.remove(fd);
        }
    }

    @Override
    public void modify(FD fd, EventSet ops) {
        if (fd instanceof VirtualFD) {
            if (virtualSocketFDs.containsKey(fd)) {
                virtualSocketFDs.get(fd).watchedEvents = ops;
            } else {
                throw new CancelledKeyExceptionWithInfo(fd.toString());
            }
        } else {
            if (fd instanceof WritableAware) {
                ops = ops.combine(EventSet.write());
            }

            modify0(fd, ops);
        }
    }

    public void modify0(FD fd, EventSet ops) {
        selector.modify(fd, ops);
    }

    public EventSet firingEvents(VirtualFD fd) {
        EventSet ret = EventSet.none();
        if (writableFired.contains(fd)) {
            ret = ret.combine(EventSet.write());
        }
        if (readableFired.contains(fd)) {
            ret = ret.combine(EventSet.read());
        }
        return ret;
    }

    @Override
    public EventSet events(FD fd) {
        if (fd instanceof VirtualFD) {
            if (virtualSocketFDs.containsKey(fd)) {
                return virtualSocketFDs.get(fd).watchedEvents;
            } else {
                throw new CancelledKeyExceptionWithInfo(fd.toString());
            }
        } else {
            return selector.events(fd);
        }
    }

    @Override
    public Object attachment(FD fd) {
        if (fd instanceof VirtualFD) {
            if (virtualSocketFDs.containsKey(fd)) {
                return virtualSocketFDs.get(fd).attachment;
            } else {
                throw new CancelledKeyExceptionWithInfo(fd.toString());
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
        for (Map.Entry<VirtualFD, REntry> e : virtualSocketFDs.entrySet()) {
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

    public void registerVirtualReadable(VirtualFD vfd) {
        if (!selector.isOpen()) {
            throw new ClosedSelectorExceptionWithInfo(this + " <- " + vfd);
        }
        if (!vfd.isOpen()) {
            Logger.error(LogType.IMPROPER_USE, "fd " + vfd + " is not open, but still trying to register readable", new Throwable());
            return;
        }
        if (!virtualSocketFDs.containsKey(vfd)) {
            Logger.warn(LogType.IMPROPER_USE, "cannot register readable for " + vfd + " when the fd not handled by this selector" +
                " Maybe it comes from a pre-registration process. You may ignore this warning if it does not keep printing.");
            return;
        }
        assert Logger.lowLevelDebug("add virtual readable: " + vfd);
        readableFired.add(vfd);

        // check fired
        var rentry = virtualSocketFDs.get(vfd);
        if (rentry == null) {
            return;
        }
        if (rentry.watchedEvents.have(Event.READABLE)) {
            wakeup();
        }
    }

    public void removeVirtualReadable(VirtualFD vfd) {
        assert Logger.lowLevelDebug("remove virtual readable: " + vfd);
        readableFired.remove(vfd);
    }

    public void registerVirtualWritable(VirtualFD vfd) {
        if (!selector.isOpen()) {
            throw new ClosedSelectorExceptionWithInfo(this + " <- " + vfd);
        }
        if (!vfd.isOpen()) {
            Logger.error(LogType.IMPROPER_USE, "fd " + vfd + " is not open, but still trying to register writable", new Throwable());
            return;
        }
        if (!virtualSocketFDs.containsKey(vfd)) {
            Logger.warn(LogType.IMPROPER_USE, "cannot register writable for " + vfd + " when the fd not handled by this selector." +
                " Maybe it comes from a pre-registration process. You may ignore this warning if it does not keep printing.");
            return;
        }
        assert Logger.lowLevelDebug("add virtual writable: " + vfd);
        writableFired.add(vfd);

        // check fired
        var rentry = virtualSocketFDs.get(vfd);
        if (rentry == null) {
            return;
        }
        if (rentry.watchedEvents.have(Event.WRITABLE)) {
            wakeup();
        }
    }

    public void removeVirtualWritable(VirtualFD vfd) {
        assert Logger.lowLevelDebug("remove virtual writable: " + vfd);
        writableFired.remove(vfd);
    }

    public void probe() {
        for (Map.Entry<VirtualFD, REntry> entry : virtualSocketFDs.entrySet()) {
            var fd = entry.getKey();
            var watch = entry.getValue().watchedEvents;
            var fire = EventSet.none();
            if (readableFired.contains(fd)) {
                fire = fire.combine(EventSet.read());
            }
            if (writableFired.contains(fd)) {
                fire = fire.combine(EventSet.write());
            }
            Logger.probe("virtual: " + fd + ", watch: " + watch + ", fire: " + fire);
        }
        for (FD fd : readableFired) {
            //noinspection SuspiciousMethodCalls
            if (!virtualSocketFDs.containsKey(fd)) {
                Logger.probe("extra readable: " + fd);
            }
        }
        for (FD fd : writableFired) {
            //noinspection SuspiciousMethodCalls
            if (!virtualSocketFDs.containsKey(fd)) {
                Logger.probe("extra writable: " + fd);
            }
        }
    }
}
