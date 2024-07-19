package io.vproxy.msquic;

import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.component.elgroup.EventLoopWrapper;
import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.callback.BlockCallback;
import io.vproxy.msquic.wrap.ApiExtraTables;
import io.vproxy.pni.PNIRef;
import io.vproxy.pni.PooledAllocator;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class MsQuicModUpcallImpl implements MsQuicModUpcall.Interface {
    private MsQuicModUpcallImpl() {
    }

    private static final MsQuicModUpcallImpl IMPL = new MsQuicModUpcallImpl();

    public static MsQuicModUpcallImpl get() {
        return IMPL;
    }

    @Override
    public int dispatch(CXPLAT_THREAD_CONFIG Config, MemorySegment EventQPtr, MemorySegment Thread, MemorySegment Context) {
        try {
            var elg = getMsQuicEventLoopGroup(Context);
            if (elg == null) {
                Logger.error(LogType.NO_EVENT_LOOP, "no event loop group provided for msquic");
                return 1;
            }
            int EventQ = EventQPtr.reinterpret(4).get(ValueLayout.JAVA_INT, 0);
            var name = elg.alias + "-" + EventQ;
            var el = elg.add(name, EventQ, new Annotations());
            if (initMsQuic(el, Config.getContext(), Thread)) {
                Logger.alert("msquic event loop is added, epfd=" + EventQ + ", el=" + el);
                return 0;
            } else {
                elg.remove(name);
                return 1;
            }
        } catch (Throwable t) {
            Logger.error(LogType.SYS_ERROR, "failed to dispatch the msquic thread", t);
            return 1;
        }
    }

    private EventLoopGroup getMsQuicEventLoopGroup(MemorySegment params) {
        if (params == null) {
            return null;
        }
        return PNIRef.getRef(params);
    }

    private boolean initMsQuic(EventLoopWrapper el, MemorySegment worker, MemorySegment thread) {
        var loop = el.getSelectorEventLoop();

        var unsafeAllocator = PooledAllocator.ofUnsafePooled();
        var locals = new CxPlatProcessEventLocals(unsafeAllocator);
        var state = new CxPlatExecutionState(unsafeAllocator);
        locals.setWorker(worker);
        locals.setState(state);

        var block = new BlockCallback<Void, Throwable>();
        loop.runOnLoop(() -> {
            try {
                ApiExtraTables.V2EXTRA.ThreadGetCur(thread);
                ApiExtraTables.V2EXTRA.ThreadSetIsWorker(true);
                MsQuicMod2.get().WorkerThreadInit(ApiExtraTables.V2EXTRA, locals);
                MsQuicMod2.get().WorkerThreadBeforePoll(ApiExtraTables.V2EXTRA, locals);
            } catch (Throwable t) {
                block.failed(t);
                return;
            }
            block.succeeded();
        });
        try {
            block.block();
        } catch (Throwable t) {
            Logger.error(LogType.SYS_ERROR, "failed to initiate msquic thread", t);
            return false;
        }
        loop.setBeforePoll(() -> {
            MsQuicMod2.get().WorkerThreadBeforePoll(ApiExtraTables.V2EXTRA, locals);
            return locals.getWaitTime();
        });
        loop.setAfterPoll((n, events) -> MsQuicMod2.get().WorkerThreadAfterPoll(ApiExtraTables.V2EXTRA, locals, n, events));
        loop.setFinalizer(() -> {
            MsQuicMod2.get().WorkerThreadFinalize(ApiExtraTables.V2EXTRA, locals);
            unsafeAllocator.close();
        });
        return true;
    }
}
