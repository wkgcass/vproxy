package io.vproxy.vfd.windows;

import io.vproxy.base.util.Lock;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.pni.Allocator;
import io.vproxy.vfd.*;
import io.vproxy.vfd.posix.AEFiredExtra;
import io.vproxy.vfd.posix.SelectedEntryPrototypeObjectList;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class IOCPSelector implements FDSelector {
    private static final int ONE_POLL_LIMIT = 128;
    final WinIOCP iocp;
    private final Allocator allocator = Allocator.ofUnsafe();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final OverlappedEntry.Array overlappedEntries = new OverlappedEntry.Array(allocator, ONE_POLL_LIMIT);
    private final List<OverlappedEntry> normalEvents = new ArrayList<>(ONE_POLL_LIMIT);
    private final List<OverlappedEntry> extraEvents = new ArrayList<>(ONE_POLL_LIMIT);
    private final AEFiredExtra.Array extraEventsNative = new AEFiredExtra.Array(allocator, ONE_POLL_LIMIT);

    private final SelectedEntryPrototypeObjectList selectedEntryList = new SelectedEntryPrototypeObjectList(ONE_POLL_LIMIT, SelectedEntry::new);

    final Lock VIRTUAL_LOCK = Lock.create();
    final Set<WindowsFD> watchedFds = new HashSet<>();
    final Set<WindowsFD> firedFds = new HashSet<>();

    public IOCPSelector(WinIOCP iocp) {
        this.iocp = iocp;
    }

    @Override
    public boolean isOpen() {
        return !iocp.isClosed();
    }

    private void checkOpen() {
        if (!isOpen()) {
            throw new ClosedSelectorException();
        }
    }

    @Override
    public Collection<SelectedEntry> select() throws IOException {
        checkOpen();
        normalEvents.clear();
        extraEvents.clear();
        int sleepMillis = -1;
        if (hasEventsToAlert()) {
            sleepMillis = 0;
        }
        iocp.getQueuedCompletionStatusEx(overlappedEntries, normalEvents, extraEvents, ONE_POLL_LIMIT, sleepMillis, false);
        return formatSelected();
    }

    @Override
    public Collection<SelectedEntry> selectNow() throws IOException {
        checkOpen();
        normalEvents.clear();
        extraEvents.clear();
        iocp.getQueuedCompletionStatusEx(overlappedEntries, normalEvents, extraEvents, ONE_POLL_LIMIT, 0, false);
        return formatSelected();
    }

    @Override
    public Collection<SelectedEntry> select(long millis) throws IOException {
        checkOpen();
        normalEvents.clear();
        extraEvents.clear();
        int sleepMillis = (int) millis;
        if (hasEventsToAlert()) {
            sleepMillis = 0;
        }
        iocp.getQueuedCompletionStatusEx(overlappedEntries, normalEvents, extraEvents, ONE_POLL_LIMIT, sleepMillis, false);
        return formatSelected();
    }

    private boolean hasEventsToAlert() {
        for (var f : firedFds) {
            if (f.firingReadable && f.watchingEvents.have(Event.READABLE)) {
                return true;
            }
            if (f.firingWritable && f.watchingEvents.have(Event.WRITABLE)) {
                return true;
            }
        }
        return false;
    }

    private Collection<SelectedEntry> formatSelected() {
        selectedEntryList.clear();
        for (var e : normalEvents) {
            var nbytes = e.getNumberOfBytesTransferred();
            var overlapped = e.getOverlapped();
            var ctx = IOCPUtils.getIOContextOf(overlapped);
            var socket = (WinSocket) ctx.getRef().getRef();
            socket.decrIORefCnt();

            if (socket.isClosed()) {
                continue;
            }

            var winfd = (WindowsFD) socket.ud;
            winfd.ioComplete(ctx, nbytes);
        }
        for (int i = 0; i < extraEvents.size(); i++) {
            var e = extraEvents.get(i);
            extraEventsNative.get(i).setUd(e.getOverlapped().MEMORY);
            extraEventsNative.get(i).setMask(e.getNumberOfBytesTransferred());
        }
        for (var fired : firedFds) {
            var ready = EventSet.none();
            if (fired.firingReadable && fired.watchingEvents.have(Event.READABLE)) {
                ready = ready.combine(EventSet.read());
            }
            if (fired.firingWritable && fired.watchingEvents.have(Event.WRITABLE)) {
                ready = ready.combine(EventSet.write());
            }
            if (ready.equals(EventSet.none())) {
                continue;
            }
            selectedEntryList.add(fired, ready, fired.attachment);
        }
        return selectedEntryList;
    }

    @Override
    public void wakeup() {
        IOCPUtils.notify(iocp);
    }

    private void checkFDMatch(FD fd) {
        var real = (WindowsFD) fd.real();
        if (!watchedFds.contains(real)) {
            throw new IllegalArgumentException("fd is not registered: " + fd);
        }
    }

    @Override
    public boolean isRegistered(FD fd) {
        //noinspection SuspiciousMethodCalls
        return watchedFds.contains(fd.real());
    }

    @Override
    public void register(FD fd, EventSet ops, Object registerData) throws ClosedChannelException {
        checkOpen();
        if (!fd.isOpen()) {
            throw new ClosedChannelException();
        }

        var winfd = (WindowsFD) fd.real();
        try (var _ = VIRTUAL_LOCK.lock()) {
            try {
                iocp.associate(winfd.socket);
            } catch (IOException e) {
                Logger.error(LogType.SYS_ERROR, "register " + fd + " to " + iocp + " failed", e);
                return;
            }
            winfd.selector = this;
            watchedFds.add(winfd);
            if (winfd.firingReadable || winfd.firingWritable) {
                firedFds.add(winfd);
            }
            winfd.watchingEvents = ops;
            winfd.attachment = registerData;
        }
    }

    @Override
    public void remove(FD fd) {
        checkOpen();
        try (var _ = VIRTUAL_LOCK.lock()) {
            //noinspection SuspiciousMethodCalls
            var removed = watchedFds.remove(fd.real());
            if (removed) {
                var winfd = (WindowsFD) fd.real();
                winfd.selector = null;
                firedFds.remove(winfd);
                winfd.watchingEvents = EventSet.none();
                winfd.attachment = null;
            }
        }
    }

    @Override
    public void modify(FD fd, EventSet ops) {
        checkOpen();
        checkFDMatch(fd);

        var winfd = (WindowsFD) fd.real();
        winfd.watchingEvents = ops;
    }

    @Override
    public EventSet events(FD fd) {
        checkOpen();
        checkFDMatch(fd);

        var winfd = (WindowsFD) fd.real();
        return winfd.watchingEvents;
    }

    @Override
    public Object attachment(FD fd) {
        checkOpen();
        checkFDMatch(fd);

        var winfd = (WindowsFD) fd.real();
        return winfd.attachment;
    }

    @Override
    public Collection<RegisterEntry> entries() {
        var result = new ArrayList<RegisterEntry>();
        for (var fd : watchedFds) {
            var entry = new RegisterEntry(fd, fd.watchingEvents, fd.attachment);
            result.add(entry);
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        if (closed.get()) {
            return;
        }
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        iocp.close();
        allocator.close();
    }

    @Override
    public int getFiredExtraNum() {
        return extraEvents.size();
    }

    @Override
    public AEFiredExtra.Array getFiredExtra() {
        return extraEventsNative;
    }
}
