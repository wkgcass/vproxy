package io.vproxy.fubuki;

import io.vproxy.pni.*;
import io.vproxy.pni.hack.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class FubukiFunc {
    private FubukiFunc() {
    }

    private static final FubukiFunc INSTANCE = new FubukiFunc();

    public static FubukiFunc get() {
        return INSTANCE;
    }

    private static final MethodHandle startMH = PanamaUtils.lookupPNICriticalFunction(new PNILinkOptions(), io.vproxy.fubuki.FubukiHandle.LAYOUT.getClass(), "fubuki_start", io.vproxy.fubuki.FubukiStartOptions.LAYOUT.getClass() /* opts */, int.class /* version */, String.class /* errorMsg */);

    public io.vproxy.fubuki.FubukiHandle start(io.vproxy.fubuki.FubukiStartOptions opts, int version, PNIString errorMsg) {
        MemorySegment RESULT;
        try {
            RESULT = (MemorySegment) startMH.invokeExact((MemorySegment) (opts == null ? MemorySegment.NULL : opts.MEMORY), version, (MemorySegment) (errorMsg == null ? MemorySegment.NULL : errorMsg.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (RESULT.address() == 0) return null;
        return RESULT == null ? null : new io.vproxy.fubuki.FubukiHandle(RESULT);
    }
}
// metadata.generator-version: pni 21.0.0.20
// sha256:9edd0bdb7b2220bb9bc06908a89d9419b9c7b4fef0e2d71855e0f08e31e5689b
