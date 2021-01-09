package vfd.type;

import java.io.IOException;

public class FDCloseReturn {
    @SuppressWarnings("InstantiationOfUtilityClass") private static final FDCloseReturn INST = new FDCloseReturn();

    private FDCloseReturn() {
    }

    @SuppressWarnings({"unused", "RedundantThrows"})
    protected FDCloseReturn(FDCloseReq req, DummyCall unused) throws IOException {
    }

    @SuppressWarnings({"unused", "RedundantThrows"})
    protected FDCloseReturn(FDCloseReq req, RealCall unused) throws IOException {
    }

    @SuppressWarnings({"unused", "RedundantThrows"})
    protected FDCloseReturn(FDCloseReq req, SuperCall unused) throws IOException {
    }

    public static FDCloseReturn nothing(@SuppressWarnings("unused") FDCloseReq req) {
        return INST;
    }

    protected static DummyCall dummyCall() {
        return DummyCall.INST;
    }

    protected static RealCall realCall() {
        return RealCall.INST;
    }

    public static class DummyCall {
        private static final DummyCall INST = new DummyCall();

        private DummyCall() {
        }
    }

    public static class RealCall {
        private static final RealCall INST = new RealCall();

        private RealCall() {
        }
    }

    public static class SuperCall {
        @SuppressWarnings("InstantiationOfUtilityClass") static final SuperCall INST = new SuperCall();

        private SuperCall() {
        }
    }
}
