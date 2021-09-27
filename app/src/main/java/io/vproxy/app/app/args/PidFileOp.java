package vproxy.app.app.args;

import vproxy.app.app.MainCtx;
import vproxy.app.app.MainOp;

public class PidFileOp implements MainOp {
    @Override
    public String key() {
        return "pidFile";
    }

    @Override
    public int argCount() {
        return 1;
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public int pre(MainCtx ctx, String[] args) {
        if (ctx.get("pidFilePathExists", false)) {
            System.err.println("pidFile already set");
            return 1;
        }
        ctx.set("pidFilePathExists", true);
        return 0;
    }

    @Override
    public int execute(MainCtx ctx, String[] args) {
        ctx.set("pidFilePath", args[0]);
        return 0;
    }
}
