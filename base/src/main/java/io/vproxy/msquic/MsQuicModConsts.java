package io.vproxy.msquic;

public class MsQuicModConsts {
    private MsQuicModConsts() {
    }

    public static final long SizeOfCxPlatProcessEventLocals = MsQuicMod.get().getCxPlatProcessEventLocalsMemorySize();
    public static final long SizeOfCXPLAT_EXECUTION_STATE = MsQuicMod.get().getCXPLAT_EXECUTION_STATEMemorySize();
}
