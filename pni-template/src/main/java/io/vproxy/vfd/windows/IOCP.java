package io.vproxy.vfd.windows;

import io.vproxy.pni.annotation.*;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

@Downcall
@Include({"ioapiset.h", "exception.h"})
interface PNIIOCP {
    @Impl(
        c = """
            ULONG nRemoved = 0;
            BOOL ok = GetQueuedCompletionStatusEx(
                handle, completionPortEntries, count,
                &nRemoved, milliseconds, alertable
            );
            if (!ok) {
                return throwIOExceptionBasedOnErrno(env);
            }
            env->return_ = nRemoved;
            return 0;
            """
    )
    int getQueuedCompletionStatusEx(
        @NativeType("HANDLE") PNIHANDLE handle,
        @Raw PNIOverlappedEntry[] completionPortEntries,
        @Unsigned int count,
        int milliseconds,
        boolean alertable
    ) throws IOException;

    @LinkerOption.Critical
    @NoAlloc
    @Impl(
        c = """
            HANDLE handle = CreateIoCompletionPort(
                fileHandle, existingCompletionPort,
                (ULONG_PTR)completionKey, numberOfConcurrentThreads
            );
            if (handle == INVALID_HANDLE_VALUE) {
                return throwIOExceptionBasedOnErrno(env);
            }
            env->return_ = (void*)handle;
            return 0;
            """
    )
    PNIHANDLE createIoCompletionPort(
        @NativeType("HANDLE") PNIHANDLE fileHandle,
        @NativeType("HANDLE") PNIHANDLE existingCompletionPort,
        MemorySegment completionKey,
        int numberOfConcurrentThreads
    ) throws IOException;

    @LinkerOption.Critical
    @Impl(
        c = """
            BOOL ok = PostQueuedCompletionStatus(
                completionPort,
                numberOfBytesTransferred,
                (ULONG_PTR)completionKey,
                overlapped
            );
            if (!ok) {
                return throwIOExceptionBasedOnErrno(env);
            }
            return 0;
            """
    )
    void postQueuedCompletionStatus(
        @NativeType("HANDLE") PNIHANDLE completionPort,
        int numberOfBytesTransferred,
        MemorySegment completionKey,
        PNIOverlapped overlapped
    ) throws IOException;
}

@Struct(skip = true)
@Include("minwinbase.h")
@Name("OVERLAPPED_ENTRY")
class PNIOverlappedEntry {
    @Name("lpCompletionKey")
    MemorySegment completionKey;
    @Name("lpOverlapped")
    @Pointer
    PNIOverlapped overlapped;
    @Name("Internal")
    MemorySegment internal;
    @Name("dwNumberOfBytesTransferred")
    int numberOfBytesTransferred;
}

@Struct(skip = true)
@Include("minwinbase.h")
@Name("OVERLAPPED")
class PNIOverlapped {
    @Name("Internal")
    long internal;
    @Name("InternalHigh")
    long internalHigh;
    @Name("DUMMYUNIONNAME")
    MemorySegment dummy;
    @Name("hEvent")
    @Pointer
    PNIHANDLE event;
}
