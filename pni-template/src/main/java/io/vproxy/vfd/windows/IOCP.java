package io.vproxy.vfd.windows;

import io.vproxy.pni.annotation.*;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

@Downcall
@Include({"IoAPI.h", "exception.h"})
interface PNIIOCP {
    @Impl(
        c = """
            BOOL ok = GetQueuedCompletionStatusEx(
                handle, completionPortEntries, count,
                numEntriesRemoved, milliseconds, alertable
            );
            if (!ok) {
                return throwIOExceptionBasedOnErrno(env);
            }
            return 0;
            """
    )
    void getQueuedCompletionStatusEx(
        @NativeType("HANDLE") PNIHANDLE handle,
        @Raw PNIOverlappedEntry[] completionPortEntries,
        @Unsigned long count,
        @Unsigned long[] numEntriesRemoved,
        int milliseconds,
        boolean alertable
    ) throws IOException;

    @LinkerOption.Critical
    @Impl(
        c = """
            HANDLE handle = CreateIoCompletionPort(
                fileHandle, existingCompletionPort,
                completionKey, numberOfConcurrentThreads
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
        @Unsigned long[] completionKey,
        int numberOfConcurrentThreads
    );

    @LinkerOption.Critical
    @Impl(
        c = """
            BOOL ok = postQueuedCompletionStatus(
                completionPort,
                numberOfBytesTransferred,
                completionKey,
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
    );
}

@Struct(skip = true)
@Include("minwinbase.h")
@Name("OVERLAPPED_ENTRY")
class PNIOverlappedEntry {
    @Name("lpCompletionKey") MemorySegment completionKey;
    @Name("lpOverlapped") PNIOverlapped overlapped;
    @Name("Internal") MemorySegment internal;
    @Name("dwNumberOfBytesTransferred") int numberOfBytesTransferred;
}

@Struct(skip = true)
@Include("minwinbase.h")
@Name("OVERLAPPED")
class PNIOverlapped {
    @Name("Internal") long internal;
    @Name("InternalHigh") long internalHigh;
    @Name("DUMMYUNIONNAME") MemorySegment dummy;
    @Name("hEvent") PNIHANDLE event;
}
