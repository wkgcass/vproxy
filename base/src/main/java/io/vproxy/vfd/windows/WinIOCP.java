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

    public WinIOCP(HANDLE handle) {
        this.handle = handle;
    }

    public WinIOCP() throws IOException {
        this.handle = IOCP.get().createIoCompletionPort(VProxyThread.current().getEnv(),
            IOCPUtils.INVALID_HANDLE, null, null, 0);
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
            IOCPUtils.notify(this.handle);
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
    }

    @Override
    public String toString() {
        return "WinIOCP(" + handle.MEMORY.address() + ")";
    }

    public void getQueuedCompletionStatusEx(OverlappedEntry.Array entries,
                                            List<OverlappedEntry> normalEvents,
                                            List<OverlappedEntry> extraEvents,
                                            int count, int milliseconds, boolean alert) throws IOException {
        var n = IOCP.get().getQueuedCompletionStatusEx(VProxyThread.current().getEnv(),
            handle, entries, count, milliseconds, alert);
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
