package vproxy.app.app.args;

import vproxy.app.app.MainCtx;
import vproxy.app.app.MainOp;
import vproxy.app.process.Shutdown;

public class SigIntDirectlyShutdownOp implements MainOp {
    @Override
    public String key() {
        return "sigIntDirectlyShutdown";
    }

    @Override
    public int argCount() {
        return 0;
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public int pre(MainCtx ctx, String[] args) {
        return 0;
    }

    @Override
    public int execute(MainCtx ctx, String[] args) {
        Shutdown.sigIntBeforeTerminate = 1;
        return 0;
    }
}
