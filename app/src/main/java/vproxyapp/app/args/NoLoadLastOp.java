package vproxyapp.app.args;

import vproxyapp.app.MainCtx;
import vproxyapp.app.MainOp;

public class NoLoadLastOp implements MainOp {
    @Override
    public String key() {
        return "noLoadLast";
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
        if (ctx.get("loaded", false)) {
            System.err.println("noLoadLast and load cannot be set together");
            return 1;
        }
        if (ctx.get("noBindCheck", false)) {
            System.err.println("noLoadLast and noStartupBindCheck cannot be set together");
            return 1;
        }
        ctx.set("noLoad", true);
        return 0;
    }

    @Override
    public int execute(MainCtx ctx, String[] args) {
        ctx.set("loaded", true); // set this flag to true, then last config won't be loaded
        return 0;
    }
}
