package vproxyapp.app.args;

import vproxyapp.app.MainCtx;
import vproxyapp.app.MainOp;
import vproxyapp.app.cmd.SystemCommand;

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
