package io.vproxy.base.selector.wrap;

import io.vproxy.base.util.Lock;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.objectpool.GarbageFree;
import io.vproxy.base.util.objectpool.PrototypeObjectList;
import io.vproxy.vfd.*;
import vproxy.vfd.*;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.*;

public class WrappedSelector implements FDSelector {
    private final FDSelector selector;
    private final PrototypeObjectList<SelectedEntry> selectedEntryList = new PrototypeObjectList<>(128, SelectedEntry::new);

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
    private final Map<FD, REntry> hybridSocketFDs = new HashMap<>();
    private final Map<VirtualFD, FD> virtual2hybridMap = new HashMap<>();
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

    @SuppressWarnings("rawtypes")
    private final Map[] virtualMaps = new Map[]{virtualSocketFDs, hybridSocketFDs};

    private void calcVirtual() {
        //noinspection unused
        try (var unused = VIRTUAL_LOCK.lock()) {
            //noinspection rawtypes
            for (Map map : virtualMaps) {
                for (Object o : map.entrySet()) {
                    //noinspection rawtypes
                    Map.Entry e = (Map.Entry) o;
                    FD fd = (FD) e.getKey();
                    REntry entry = (REntry) e.getValue();

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
                        selectedEntryList.add().set(fd, eventSet, entry.attachment);
                    }

                    if (writable) {
                        if (fd instanceof WritableAware) {
                            ((WritableAware) fd).writable();
                        }
                    }
                }
            }
        }
    }

    private void handleRealSelect(Collection<SelectedEntry> entries) {
        for (SelectedEntry entry : entries) {
            if (entry.fd() instanceof WritableAware) {
                if (entry.ready().have(Event.WRITABLE)) {
                    ((WritableAware) entry.fd()).writable();
                }
            }
            selectedEntryList.add().set(entry.fd(), entry.ready(), entry.attachment());
        }
    }

    @GarbageFree
    @Override
    public Collection<SelectedEntry> select() throws IOException {
        selectedEntryList.clear();

        calcVirtual();
        if (selectedEntryList.isEmpty()) {
            handleRealSelect(selector.select());
        } else {
            handleRealSelect(selector.selectNow());
        }
        return selectedEntryList;
    }

    @GarbageFree
    @Override
    public Collection<SelectedEntry> selectNow() throws IOException {
        selectedEntryList.clear();

        calcVirtual();
        handleRealSelect(selector.selectNow());
        return selectedEntryList;
    }

    @GarbageFree
    @Override
    public Collection<SelectedEntry> select(long millis) throws IOException {
        selectedEntryList.clear();

        calcVirtual();
        if (selectedEntryList.isEmpty()) {
            handleRealSelect(selector.select(millis));
        } else {
            handleRealSelect(selector.selectNow());
        }
        return selectedEntryList;
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
        } else if (fd.real() instanceof VirtualFD) {
            return hybridSocketFDs.containsKey(fd);
        } else {
            return selector.isRegistered(fd);
        }
    }

    @Override
    public void register(FD fd, EventSet ops, Object registerData) throws ClosedChannelException {
        assert Logger.lowLevelDebug("register fd to selector " + fd);

        if (fd instanceof WritableAware) {
            ops = ops.combine(EventSet.write());
        }

        if (fd instanceof VirtualFD) {
            assert Logger.lowLevelDebug("register virtual fd to selector");
            //noinspection unused
            try (var unused = VIRTUAL_LOCK.lock()) {
                virtualSocketFDs.put((VirtualFD) fd, new REntry(ops, registerData));
            }
            ((VirtualFD) fd).onRegister();
        } else if (fd.real() instanceof VirtualFD) {
            assert Logger.lowLevelDebug("register hybrid fd to selector");
            //noinspection unused
            try (var unused = VIRTUAL_LOCK.lock()) {
                hybridSocketFDs.put(fd, new REntry(ops, registerData));
                virtual2hybridMap.put((VirtualFD) fd.real(), fd);
            }
            ((VirtualFD) fd.real()).onRegister();
        } else {
            assert Logger.lowLevelDebug("register real fd to selector");
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
        } else if (fd.real() instanceof VirtualFD) {
            hybridSocketFDs.remove(fd);
            virtual2hybridMap.remove((VirtualFD) fd.real());
            readableFired.remove(fd);
            writableFired.remove(fd);
            ((VirtualFD) fd.real()).onRemove();
        } else {
            selector.remove(fd);
        }
    }

    @Override
    public void modify(FD fd, EventSet ops) {
        if (fd instanceof WritableAware) {
            ops = ops.combine(EventSet.write());
        }
        if (fd instanceof VirtualFD) {
            if (virtualSocketFDs.containsKey(fd)) {
                virtualSocketFDs.get(fd).watchedEvents = ops;
            } else {
                throw new CancelledKeyExceptionWithInfo(fd.toString());
            }
        } else {
            modify0(fd, ops);
        }
    }

    public void modify0(FD fd, EventSet ops) {
        if (fd.real() instanceof VirtualFD) {
            if (hybridSocketFDs.containsKey(fd)) {
                hybridSocketFDs.get(fd).watchedEvents = ops;
            } else {
                throw new CancelledKeyExceptionWithInfo(fd.toString());
            }
        } else {
            selector.modify(fd, ops);
        }
    }

    public EventSet firingEvents(VirtualFD fd) {
        EventSet ret = EventSet.none();
        var hybrid = virtual2hybridMap.get(fd);
        if (hybrid == null) {
            if (writableFired.contains(fd)) {
                ret = ret.combine(EventSet.write());
            }
            if (readableFired.contains(fd)) {
                ret = ret.combine(EventSet.read());
            }
        } else {
            if (writableFired.contains(hybrid)) {
                ret = ret.combine(EventSet.write());
            }
            if (readableFired.contains(hybrid)) {
                ret = ret.combine(EventSet.read());
            }
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
        } else if (fd.real() instanceof VirtualFD) {
            if (hybridSocketFDs.containsKey(fd)) {
                return hybridSocketFDs.get(fd).watchedEvents;
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
        } else if (fd.real() instanceof VirtualFD) {
            if (hybridSocketFDs.containsKey(fd)) {
                return hybridSocketFDs.get(fd).attachment;
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
        if (virtualSocketFDs.isEmpty() && hybridSocketFDs.isEmpty()) {
            return selectorRet;
        }

        Set<RegisterEntry> ret = new HashSet<>(selectorRet);
        //noinspection rawtypes
        for (Map map : virtualMaps) {
            for (Object o : map.entrySet()) {
                //noinspection rawtypes
                Map.Entry e = (Map.Entry) o;
                var fd = (FD) e.getKey();
                var entry = (REntry) e.getValue();
                ret.add(new RegisterEntry(fd, entry.watchedEvents, entry.attachment));
            }
        }
        return ret;
    }

    @Override
    public void close() throws IOException {
        virtualSocketFDs.clear();
        hybridSocketFDs.clear();
        virtual2hybridMap.clear();
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
        if (!virtualSocketFDs.containsKey(vfd) && !virtual2hybridMap.containsKey(vfd)) {
            assert Logger.lowLevelDebug("cannot register readable for " + vfd + " when the fd not handled by this selector." +
                " Maybe it comes from a pre-registration process. You may ignore this warning if it does not keep printing.");
            return;
        }
        if (virtual2hybridMap.containsKey(vfd)) {
            FD hybrid = virtual2hybridMap.get(vfd);
            assert Logger.lowLevelDebug("add hybrid readable: " + hybrid);
            readableFired.add(hybrid);

            // check fired
            var rentry = hybridSocketFDs.get(hybrid);
            if (rentry.watchedEvents.have(Event.READABLE)) {
                wakeup();
            }
        } else {
            assert Logger.lowLevelDebug("add virtual readable: " + vfd);
            readableFired.add(vfd);

            // check fired
            var rentry = virtualSocketFDs.get(vfd);
            if (rentry.watchedEvents.have(Event.READABLE)) {
                wakeup();
            }
        }
    }

    public void removeVirtualReadable(VirtualFD vfd) {
        var hybrid = virtual2hybridMap.get(vfd);
        if (hybrid == null) {
            assert Logger.lowLevelDebug("remove virtual readable: " + vfd);
            readableFired.remove(vfd);
        } else {
            assert Logger.lowLevelDebug("remove hybrid readable: " + vfd);
            readableFired.remove(hybrid);
        }
    }

    public void registerVirtualWritable(VirtualFD vfd) {
        if (!selector.isOpen()) {
            throw new ClosedSelectorExceptionWithInfo(this + " <- " + vfd);
        }
        if (!vfd.isOpen()) {
            Logger.error(LogType.IMPROPER_USE, "fd " + vfd + " is not open, but still trying to register writable", new Throwable());
            return;
        }
        if (!virtualSocketFDs.containsKey(vfd) && !virtual2hybridMap.containsKey(vfd)) {
            assert Logger.lowLevelDebug("cannot register writable for " + vfd + " when the fd not handled by this selector." +
                " Maybe it comes from a pre-registration process. You may ignore this warning if it does not keep printing.");
            return;
        }
        if (virtual2hybridMap.containsKey(vfd)) {
            FD hybrid = virtual2hybridMap.get(vfd);
            assert Logger.lowLevelDebug("add hybrid writable: " + hybrid);
            writableFired.add(hybrid);

            // check fired
            var rentry = hybridSocketFDs.get(hybrid);
            if (rentry.watchedEvents.have(Event.WRITABLE)) {
                wakeup();
            }
        } else {
            assert Logger.lowLevelDebug("add virtual writable: " + vfd);
            writableFired.add(vfd);

            // check fired
            var rentry = virtualSocketFDs.get(vfd);
            if (rentry.watchedEvents.have(Event.WRITABLE)) {
                wakeup();
            }
        }
    }

    public void removeVirtualWritable(VirtualFD vfd) {
        var hybrid = virtual2hybridMap.get(vfd);
        if (hybrid == null) {
            assert Logger.lowLevelDebug("remove virtual writable: " + vfd);
            writableFired.remove(vfd);
        } else {
            assert Logger.lowLevelDebug("remove hybrid writable: " + hybrid);
            writableFired.remove(hybrid);
        }
    }

    public void copyFDEvents(Collection<FDInspection> coll) {
        //noinspection rawtypes
        for (Map map : virtualMaps) {
            for (Object o : map.entrySet()) {
                //noinspection rawtypes
                var entry = (Map.Entry) o;

                var fd = (FD) entry.getKey();
                var watch = ((REntry) entry.getValue()).watchedEvents;
                var fire = EventSet.none();
                if (readableFired.contains(fd)) {
                    fire = fire.combine(EventSet.read());
                }
                if (writableFired.contains(fd)) {
                    fire = fire.combine(EventSet.write());
                }
                coll.add(new FDInspection(fd, watch, fire));
            }
        }
        for (var fd : virtual2hybridMap.keySet()) {
            coll.add(new FDInspection(fd, null, null));
        }
        for (var entry : selector.entries()) {
            var fd = entry.fd;
            var watch = entry.eventSet;
            coll.add(new FDInspection(fd, watch, null));
        }
    }
}
