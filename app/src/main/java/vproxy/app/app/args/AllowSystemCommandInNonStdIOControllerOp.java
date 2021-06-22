package vproxy.app.app.args;

import vproxy.app.app.MainCtx;
import vproxy.app.app.MainOp;
import vproxy.app.app.cmd.SystemCommand;

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
