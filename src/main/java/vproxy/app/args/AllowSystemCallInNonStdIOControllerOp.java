package vproxy.app.args;

import vproxy.app.MainOp;
import vproxy.app.MainCtx;
import vproxy.app.cmd.SystemCommand;

public class AllowSystemCallInNonStdIOControllerOp implements MainOp {
    @Override
    public String key() {
        return "allowSystemCallInNonStdIOController";
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
