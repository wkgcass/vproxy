package io.vproxy.msquic.modified;

public class MsQuicConsts {
    private MsQuicConsts() {
    }

    public static final long SizeOfCxPlatProcessEventLocals = MsQuic.get().getCxPlatProcessEventLocalsMemorySize();
    public static final long SizeOfCXPLAT_EXECUTION_STATE = MsQuic.get().getCXPLAT_EXECUTION_STATEMemorySize();
}
