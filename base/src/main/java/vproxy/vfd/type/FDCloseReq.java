package vproxy.vfd.type;

import java.io.IOException;
import java.util.function.Function;

public class FDCloseReq {
    private static final FDCloseReq INST = new FDCloseReq();

    private FDCloseReq() {
    }

    public static FDCloseReq inst() {
        return INST;
    }

    public <T extends FDCloseReturn> T superClose(CallSuperCloseFunction<T> f) throws IOException {
        var superCall = FDCloseReturn.SuperCall.INST;
        return f.apply(this, superCall);
    }

    public <T extends FDCloseReturn> void wrapClose(Function<FDCloseReq, T> f) {
        T closeReturn = f.apply(this);
        if (closeReturn == null) {
            throw new Error("IMPLEMENTATION ERROR!!! the close(x) method must return a CloseReturn object");
        }
    }

    @FunctionalInterface
    public interface CallSuperCloseFunction<T extends FDCloseReturn> {
        T apply(FDCloseReq req, FDCloseReturn.SuperCall superCall) throws IOException;
    }

    public interface CallCloseFunction<T extends FDCloseReturn> {
        T apply(FDCloseReq req) throws IOException;
    }
}
