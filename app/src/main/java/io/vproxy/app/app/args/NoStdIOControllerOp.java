package io.vproxy.app.app.args;

import io.vproxy.app.app.MainCtx;
import io.vproxy.app.app.MainOp;

public class NoStdIOControllerOp implements MainOp {
    @Override
    public String key() {
        return "noStdIOController";
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
        ctx.set("noStdIOController", true);
        return 0;
    }
}
