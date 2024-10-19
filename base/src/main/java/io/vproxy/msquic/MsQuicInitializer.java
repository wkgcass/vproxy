package io.vproxy.msquic;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.msquic.wrap.ApiExtraTables;
import io.vproxy.msquic.wrap.ApiTables;
import io.vproxy.pni.Allocator;

import java.util.NoSuchElementException;

import static io.vproxy.msquic.MsQuicConsts.QUIC_PARAM_GLOBAL_EXECUTION_CONFIG;

public class MsQuicInitializer {
    private static boolean initialized = false;
    private static boolean supported = false;

    public static boolean isSupported() {
        if (initialized) {
            return supported;
        }
        try {
            Utils.loadDynamicLibrary("msquic");
            Utils.loadDynamicLibrary("msquic-java");
        } catch (UnsatisfiedLinkError e) {
            Logger.error(LogType.SYS_ERROR, "unable to load quic support", e);
            supported = false;
            initialized = true;
            return false;
        }

        MsQuicUpcall.setImpl(MsQuicUpcallImpl.get());
        MsQuicModUpcall.setImpl(MsQuicModUpcallImpl.get());
        ApiExtraTables.V2EXTRA.EventLoopThreadDispatcherSet(MsQuicModUpcall.dispatch);

        // FIXME: need to implement event loop migration
        try (var allocator = Allocator.ofConfined()) {
            int cpucnt = 1;
            var config = new QuicExecutionConfig(allocator.allocate(QuicExecutionConfig.LAYOUT.byteSize()));
            config.setProcessorCount(cpucnt);
            config.getProcessorList().set(0, (short) 0);
            ApiTables.V2.opts.apiTableQ.setParam(QUIC_PARAM_GLOBAL_EXECUTION_CONFIG, (int) config.MEMORY.byteSize(), config.MEMORY);
        }

        supported = true;
        initialized = true;
        return true;
    }

    public static IsSupported getIsSupported() {
        if (isSupported()) {
            return IsSupported.IMPL;
        } else {
            throw new NoSuchElementException("msquic is not supported");
        }
    }

    public static class IsSupported {
        private IsSupported() {
        }

        private static final IsSupported IMPL = new IsSupported();
    }
}
