package io.vproxy.msquic;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;

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
            Utils.loadDynamicLibrary("vpquic");
        } catch (UnsatisfiedLinkError e) {
            Logger.error(LogType.SYS_ERROR, "unable to load quic support", e);
            supported = false;
            initialized = true;
            return false;
        }

        MsQuicUpcall.setImpl(MsQuicUpcallImpl.get());
        MsQuicModUpcall.setImpl(MsQuicModUpcallImpl.get());
        MsQuicMod.get().MsQuicSetEventLoopThreadDispatcher();

        supported = true;
        initialized = true;
        return true;
    }
}
