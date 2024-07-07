package io.vproxy.msquic;

import io.vproxy.pni.annotation.*;
import io.vproxy.vfd.posix.PNIAEFiredExtra;

import java.lang.foreign.MemorySegment;

@Downcall
@Include("msquic.h")
interface PNIMsQuicMod2 {
    @Impl(
        // language="c"
        c = """
            api->WorkerThreadInit(CxPlatWorkerThreadLocals);
            """
    )
    @Style(Styles.critical)
    void WorkerThreadInit(PNIQuicExtraApiTable api, PNICxPlatProcessEventLocals CxPlatWorkerThreadLocals);

    @Impl(
        // language="c"
        c = """
            api->WorkerThreadBeforePoll(CxPlatProcessEventLocals);
            """
    )
    @Style(Styles.critical)
    void WorkerThreadBeforePoll(PNIQuicExtraApiTable api, PNICxPlatProcessEventLocals CxPlatProcessEventLocals);

    @Impl(
        // language="c"
        c = """
            locals->CqeCount = num;
            for (int i = 0; i < num && i < CxPlatProcessCqesArraySize; ++i) {
            #ifdef __linux__
                locals->Cqes[i].data.ptr = events[i].ud;
                locals->Cqes[i].events = events[i].mask;
            #elif defined(__APPLE__)
                locals->Cqes[i].udata = events[i].ud;
                locals->Cqes[i].filter = events[i].mask;
            #else // windows
                locals->Cqes[i].lpOverlapped = events[i].ud;
                locals->Cqes[i].dwNumberOfBytesTransferred = events[i].mask;
            #endif
            }
            int ret = api->WorkerThreadAfterPoll(locals);
            return ret;
            """
    )
    @Style(Styles.critical)
    boolean WorkerThreadAfterPoll(PNIQuicExtraApiTable api,
                                              PNICxPlatProcessEventLocals locals,
                                              int num,
                                              @Raw PNIAEFiredExtra[] events);

    @Impl(
        // language="c"
        c = """
            return api->WorkerThreadFinalize(CxPlatWorkerThreadLocals);
            """
    )
    @Style(Styles.critical)
    int WorkerThreadFinalize(PNIQuicExtraApiTable api, PNICxPlatProcessEventLocals CxPlatWorkerThreadLocals);
}

@Struct(skip = true, typedef = false)
@AlwaysAligned
@Include("msquic.h")
@Sizeof("struct CxPlatProcessEventLocals")
class PNICxPlatProcessEventLocals {
    MemorySegment worker;
    @Pointer PNICxPlatExecutionState state;
    @Unsigned int waitTime;

    // other fields are not used in java
}

@Struct(skip = true)
@Include("msquic.h")
@Name("CXPLAT_EXECUTION_STATE")
@Sizeof("CXPLAT_EXECUTION_STATE")
@PointerOnly
class PNICxPlatExecutionState {
}
