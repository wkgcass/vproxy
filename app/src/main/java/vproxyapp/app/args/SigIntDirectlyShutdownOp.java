package vproxyapp.app.args;

import vproxyapp.app.MainCtx;
import vproxyapp.app.MainOp;
import vproxyapp.process.Shutdown;

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
