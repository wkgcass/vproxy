package io.vproxy.vfd.windows;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.base.util.unsafe.SunUnsafe;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class WinIOCP {
    public final HANDLE handle;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<Notification> notifications = new ConcurrentLinkedQueue<>();
    boolean polling = false;
    boolean notified = false;

    public WinIOCP(HANDLE handle) {
        this.handle = handle;
    }

    public WinIOCP() throws IOException {
        this(0);
    }

    public WinIOCP(int concurrency) throws IOException {
        this.handle = IOCP.get().createIoCompletionPort(VProxyThread.current().getEnv(),
            IOCPUtils.INVALID_HANDLE, null, null, concurrency);
    }

    public void associate(WinSocket socket) throws IOException {
        if (isClosed())
            throw new IOException("iocp is closed");

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (socket) {
            var alreadyAssociated = socket.getIocp();
            if (alreadyAssociated == this) {
                return; // already associated
            }
            if (alreadyAssociated != null) {
                throw new IOException(socket + " is already associated to " + alreadyAssociated);
            }
            socket.iocp = this;
        }
        boolean needNotify = false;
        Notification notif;
        while ((notif = socket.notifications.poll()) != null) {
            notifications.add(notif);
            needNotify = true;
        }
        if (needNotify) {
            IOCPUtils.notify(this);
        }
    }

    public void dissociate(WinSocket socket) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (socket) {
            var iocp = socket.getIocp();
            if (iocp == this) {
                socket.iocp = null;
            }
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    public void close() {
        if (closed.get()) {
            return;
        }
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            WindowsNative.get().closeHandle(VProxyThread.current().getEnv(), new SOCKET(handle.MEMORY));
        } catch (IOException e) {
            Logger.error(LogType.SYS_ERROR, "closing iocp " + handle + " failed", e);
        }
        Notification notif;
        while ((notif = notifications.poll()) != null) {
            var ctx = IOCPUtils.getIOContextOf(notif.overlapped);
            var socket = (WinSocket) ctx.getRef().getRef();
            if (socket.isClosed()) {
                Logger.warn(LogType.SOCKET_ERROR, socket + " and " + this + " are closed, while pending io operation is done");
                socket.decrIORefCnt();
            } else {
                socket.notifications.add(notif);
            }
        }
    }

    @Override
    public String toString() {
        return "WinIOCP(" + handle.MEMORY.address() + ")";
    }

    public void getQueuedCompletionStatusEx(OverlappedEntry.Array entries,
                                            List<OverlappedEntry> normalEvents,
                                            List<OverlappedEntry> extraEvents,
                                            int count, int milliseconds, boolean alert) throws IOException {
        if (milliseconds != 0) {
            // need to poll directly because there are notifications
            if (!notifications.isEmpty()) {
                milliseconds = 0;
            }
        }
        if (milliseconds != 0) {
            synchronized (this) {
                if (notified) {
                    // need to poll directly because it's notified
                    milliseconds = 0;
                } else {
                    // if it's not notified, we go for polling
                    polling = true;
                }
            }
        }

        var n = IOCP.get().getQueuedCompletionStatusEx(VProxyThread.current().getEnv(),
            handle, entries, count, milliseconds, alert);

        notified = false;
        polling = false;

        for (int i = 0; i < n; ++i) {
            var entry = entries.get(i);
            var ctx = IOCPUtils.getIOContextOf(entry.getOverlapped());
            if (IOCPUtils.VPROXY_CTX_TYPE != IOCPUtils.getContextType(ctx)) {
                extraEvents.add(entry);
                continue;
            }
            if (ctx.getIoType() == IOType.NOTIFY.code) {
                SunUnsafe.freeMemory(ctx.MEMORY.address());
                continue;
            }
            normalEvents.add(entry);
        }
        Notification notif;
        while (n < count && ((notif = notifications.poll()) != null)) {
            var target = entries.get(n++);
            target.setCompletionKey(notif.completionKey);
            target.setOverlapped(notif.overlapped);
            target.setNumberOfBytesTransferred(notif.transferredBytes);
            normalEvents.add(target);
        }
    }

    record Notification(
        int transferredBytes,
        MemorySegment completionKey,
        Overlapped overlapped) {
    }

    // invoked on the underlying-iocp threads
    void postEvent(Notification notif) {
        notifications.add(notif);
    }
}
