package io.vproxy.vfd.windows;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.pni.Allocator;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class UnderlyingIOCP {
    private static UnderlyingIOCP INST;

    public static UnderlyingIOCP get() {
        if (INST != null) {
            return INST;
        }
        synchronized (UnderlyingIOCP.class) {
            if (INST != null) {
                return INST;
            }
            INST = new UnderlyingIOCP();
        }
        return INST;
    }

    private final HANDLE iocp;

    private UnderlyingIOCP() {
        try {
            this.iocp = IOCP.get().createIoCompletionPort(VProxyThread.current().getEnv(),
                IOCPUtils.INVALID_HANDLE, null, null, 0);
        } catch (IOException e) {
            Logger.error(LogType.SYS_ERROR, "unable to create the underlying iocp", e);
            throw new Error(e);
        }

        var str = Utils.getSystemProperty("underlying_iocp_thread_num");
        int nThreads;
        if (str == null) {
            nThreads = Runtime.getRuntime().availableProcessors();
        } else {
            nThreads = Integer.parseInt(str);
        }
        for (var i = 0; i < nThreads; i++) {
            VProxyThread.create(this::loop, "underlying-iocp-" + i + "/" + nThreads).start();
        }
    }

    private static final int PER_LOOP = 256;

    private void loop() {
        var notifyIOCPs = new HashSet<WinIOCP>();
        try (var allocator = Allocator.ofConfined()) {
            var entries = new OverlappedEntry.Array(allocator, PER_LOOP);
            //noinspection InfiniteLoopStatement
            while (true) {
                notifyIOCPs.clear();
                try {
                    oneLoop(entries, notifyIOCPs);
                } catch (Throwable t) {
                    Logger.fatal(LogType.SYS_ERROR, "underlying iocp loop got exception", t);
                }
            }
        }
    }

    private void oneLoop(OverlappedEntry.Array entries, Set<WinIOCP> notifyIOCPs) throws Throwable {
        int n = IOCP.get().getQueuedCompletionStatusEx(VProxyThread.current().getEnv(),
            iocp, entries, PER_LOOP, -1, false);
        for (int i = 0; i < n; ++i) {
            try {
                oneEvent(entries.get(i), notifyIOCPs);
            } catch (Throwable t) {
                Logger.fatal(LogType.SYS_ERROR, "underlying iocp loop got exception", t);
            }
        }
        if (notifyIOCPs.isEmpty()) {
            return;
        }
        for (var iocp : notifyIOCPs) {
            IOCPUtils.notify(iocp.handle);
        }
        notifyIOCPs.clear();
    }

    private void oneEvent(OverlappedEntry entry, Set<WinIOCP> alertIOCPs) {
        var ctx = IOCPUtils.getIOContextOf(entry.getOverlapped());
        var winSocket = (WinSocket) ctx.getRef().getRef();

        var transferred = entry.getNumberOfBytesTransferred();
        var completionKey = entry.getCompletionKey();
        var overlapped = entry.getOverlapped();
        var notif = new WinIOCP.Notification(transferred, completionKey, overlapped);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (winSocket) {
            var iocp = winSocket.getIocp();
            if (iocp == null) {
                assert Logger.lowLevelDebug(winSocket + " is not associated with iocp, push notification to itself");
                winSocket.notifications.add(notif);
                return;
            }
            iocp.postEvent(notif);
            alertIOCPs.add(iocp);
        }
    }

    void associate(SOCKET socket) {
        try {
            IOCP.get().createIoCompletionPort(VProxyThread.current().getEnv(),
                socket, iocp, null, 0);
        } catch (IOException e) {
            Logger.fatal(LogType.SYS_ERROR, "failed to associate " + socket + " to the underlying iocp");
        }
    }
}
