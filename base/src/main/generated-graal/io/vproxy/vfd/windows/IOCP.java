package io.vproxy.vfd.windows;

import io.vproxy.pni.*;
import io.vproxy.pni.hack.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;
import io.vproxy.pni.graal.*;
import org.graalvm.nativeimage.*;
import org.graalvm.nativeimage.c.function.*;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.WordFactory;

public class IOCP {
    private IOCP() {
    }

    private static final IOCP INSTANCE = new IOCP();

    public static IOCP get() {
        return INSTANCE;
    }

    private static final MethodHandle getQueuedCompletionStatusExMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions(), "Java_io_vproxy_vfd_windows_IOCP_getQueuedCompletionStatusEx", io.vproxy.vfd.windows.HANDLE.LAYOUT.getClass() /* handle */, MemorySegment.class /* completionPortEntries */, int.class /* count */, int.class /* milliseconds */, boolean.class /* alertable */);

    public int getQueuedCompletionStatusEx(PNIEnv ENV, io.vproxy.vfd.windows.HANDLE handle, io.vproxy.vfd.windows.OverlappedEntry.Array completionPortEntries, int count, int milliseconds, boolean alertable) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) getQueuedCompletionStatusExMH.invokeExact(ENV.MEMORY, (MemorySegment) (handle == null ? MemorySegment.NULL : handle.MEMORY), (MemorySegment) (completionPortEntries == null ? MemorySegment.NULL : completionPortEntries.MEMORY), count, milliseconds, alertable);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle createIoCompletionPortMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_vfd_windows_IOCP_createIoCompletionPort", io.vproxy.vfd.windows.SOCKET.LAYOUT.getClass() /* fileHandle */, io.vproxy.vfd.windows.HANDLE.LAYOUT.getClass() /* existingCompletionPort */, MemorySegment.class /* completionKey */, int.class /* numberOfConcurrentThreads */);

    public io.vproxy.vfd.windows.HANDLE createIoCompletionPort(PNIEnv ENV, io.vproxy.vfd.windows.SOCKET fileHandle, io.vproxy.vfd.windows.HANDLE existingCompletionPort, MemorySegment completionKey, int numberOfConcurrentThreads) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) createIoCompletionPortMH.invokeExact(ENV.MEMORY, (MemorySegment) (fileHandle == null ? MemorySegment.NULL : fileHandle.MEMORY), (MemorySegment) (existingCompletionPort == null ? MemorySegment.NULL : existingCompletionPort.MEMORY), (MemorySegment) (completionKey == null ? MemorySegment.NULL : completionKey), numberOfConcurrentThreads);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        var RESULT = ENV.returnPointer();
        return RESULT == null ? null : new io.vproxy.vfd.windows.HANDLE(RESULT);
    }

    private static final MethodHandle postQueuedCompletionStatusMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_vfd_windows_IOCP_postQueuedCompletionStatus", io.vproxy.vfd.windows.HANDLE.LAYOUT.getClass() /* completionPort */, int.class /* numberOfBytesTransferred */, MemorySegment.class /* completionKey */, io.vproxy.vfd.windows.Overlapped.LAYOUT.getClass() /* overlapped */);

    public void postQueuedCompletionStatus(PNIEnv ENV, io.vproxy.vfd.windows.HANDLE completionPort, int numberOfBytesTransferred, MemorySegment completionKey, io.vproxy.vfd.windows.Overlapped overlapped) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) postQueuedCompletionStatusMH.invokeExact(ENV.MEMORY, (MemorySegment) (completionPort == null ? MemorySegment.NULL : completionPort.MEMORY), numberOfBytesTransferred, (MemorySegment) (completionKey == null ? MemorySegment.NULL : completionKey), (MemorySegment) (overlapped == null ? MemorySegment.NULL : overlapped.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }
}
// metadata.generator-version: pni 22.0.0.20
// sha256:2200f5d7c939999bc28bea1fd795889f3edd0ff44de3e8bacd125565849e4764
