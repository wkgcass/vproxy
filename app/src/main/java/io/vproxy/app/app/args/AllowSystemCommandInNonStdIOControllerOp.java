package io.vproxy.app.app.args;

import io.vproxy.app.app.MainCtx;
import io.vproxy.app.app.MainOp;
import io.vproxy.app.app.cmd.SystemCommand;

public class AllowSystemCommandInNonStdIOControllerOp implements MainOp {
    @Override
    public String key() {
        return "allowSystemCommandInNonStdIOController";
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
        SystemCommand.allowNonStdIOController = true;
        return 0;
    }
}
