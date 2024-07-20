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
    private final int[] extraEventsNum = {0};
    private final AEFiredExtra.Array extraEventsNative = new AEFiredExtra.Array(allocator, ONE_POLL_LIMIT);

    private final SelectedEntryPrototypeObjectList selectedEntryList = new SelectedEntryPrototypeObjectList(ONE_POLL_LIMIT, SelectedEntry::new);

    final Lock VIRTUAL_LOCK = Lock.create();
    final Map<WindowsFD, FD> watchedFds = new HashMap<>(); // real -> fd
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
        int sleepMillis = preSelect(-1);
        iocp.getQueuedCompletionStatusEx(overlappedEntries, normalEvents, extraEventsNative, extraEventsNum, ONE_POLL_LIMIT, sleepMillis, false);
        return formatSelected();
    }

    @Override
    public Collection<SelectedEntry> selectNow() throws IOException {
        int sleepMillis = preSelect(0);
        selectedEntryList.clear();
        iocp.getQueuedCompletionStatusEx(overlappedEntries, normalEvents, extraEventsNative, extraEventsNum, ONE_POLL_LIMIT, sleepMillis, false);
        return formatSelected();
    }

    @Override
    public Collection<SelectedEntry> select(long millis) throws IOException {
        int sleepMillis = preSelect(millis);
        iocp.getQueuedCompletionStatusEx(overlappedEntries, normalEvents, extraEventsNative, extraEventsNum, ONE_POLL_LIMIT, sleepMillis, false);
        return formatSelected();
    }

    private int preSelect(long sleepMillis) {
        checkOpen();
        normalEvents.clear();
        selectedEntryList.clear();
        if (sleepMillis == 0) {
            return 0;
        }
        if (hasEventsToAlert()) {
            return 0;
        }
        return (int) sleepMillis;
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
            var ntstatus = overlapped.getInternal();
            if (ntstatus != 0) {
                winfd.ioError(ctx, (int) (ntstatus & 0xffffffffL));
            } else {
                winfd.ioComplete(ctx, nbytes);
            }
        }
        for (var iterator = firedFds.iterator(); iterator.hasNext(); ) {
            var fired = iterator.next();
            if (!fired.isOpen()) {
                assert Logger.lowLevelDebug(fired + " is closed, clear events of this fd");
                watchedFds.remove(fired);
                iterator.remove();
                continue;
            }

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
            var registered = watchedFds.get(fired);
            if (registered == null) {
                Logger.error(LogType.SYS_ERROR, fired + " is not registered into " + this + " but firing events");
                continue;
            }
            selectedEntryList.add(registered, ready, fired.attachment);
        }
        return selectedEntryList;
    }

    @Override
    public void wakeup() {
        IOCPUtils.notify(iocp);
    }

    private void checkFDMatch(FD fd) {
        var real = (WindowsFD) fd.real();
        if (!watchedFds.containsKey(real)) {
            throw new IllegalArgumentException("fd is not registered: " + fd);
        }
    }

    @Override
    public boolean isRegistered(FD fd) {
        //noinspection SuspiciousMethodCalls
        return watchedFds.containsKey(fd.real());
    }

    @Override
    public void register(FD fd, EventSet ops, Object registerData) throws ClosedChannelException {
        checkOpen();
        if (!fd.isOpen()) {
            throw new ClosedChannelException();
        }

        var real = (WindowsFD) fd.real();
        try (var _ = VIRTUAL_LOCK.lock()) {
            try {
                iocp.associate(real.socket);
            } catch (IOException e) {
                Logger.error(LogType.SYS_ERROR, "register " + fd + " to " + iocp + " failed", e);
                return;
            }
            real.selector = this;
            watchedFds.put(real, fd);
            if (real.firingReadable || real.firingWritable) {
                firedFds.add(real);
            }
            real.watchingEvents = ops;
            real.attachment = registerData;
        }
        IOCPUtils.notify(iocp);
    }

    @Override
    public void remove(FD fd) {
        checkOpen();
        try (var _ = VIRTUAL_LOCK.lock()) {
            //noinspection SuspiciousMethodCalls
            var removed = watchedFds.remove(fd.real());
            if (removed != null) {
                var real = (WindowsFD) fd.real();
                real.selector = null;
                firedFds.remove(real);
                real.watchingEvents = EventSet.none();
                real.attachment = null;
                iocp.dissociate(real.socket);
            }
        }
    }

    @Override
    public void modify(FD fd, EventSet ops) {
        checkOpen();
        checkFDMatch(fd);

        var real = (WindowsFD) fd.real();
        real.watchingEvents = ops;

        IOCPUtils.notify(iocp);
    }

    @Override
    public EventSet events(FD fd) {
        checkOpen();
        checkFDMatch(fd);

        var real = (WindowsFD) fd.real();
        return real.watchingEvents;
    }

    @Override
    public Object attachment(FD fd) {
        checkOpen();
        checkFDMatch(fd);

        var real = (WindowsFD) fd.real();
        return real.attachment;
    }

    @Override
    public Collection<RegisterEntry> entries() {
        var result = new ArrayList<RegisterEntry>();
        for (var entry : watchedFds.entrySet()) {
            var real = entry.getKey();
            var fd = entry.getValue();
            var rentry = new RegisterEntry(fd, real.watchingEvents, real.attachment);
            result.add(rentry);
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
        return extraEventsNum[0];
    }

    @Override
    public AEFiredExtra.Array getFiredExtra() {
        return extraEventsNative;
    }

    @Override
    public String toString() {
        var openclosed = "[closed]";
        if (isOpen()) {
            openclosed = "[open]";
        }
        return "IOCPSelector:" + iocp + openclosed;
    }
}
