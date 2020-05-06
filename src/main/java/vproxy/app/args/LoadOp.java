package vproxy.app.args;

import vproxy.app.Main;
import vproxy.app.MainCtx;
import vproxy.app.MainOp;
import vproxy.component.app.Shutdown;
import vproxy.util.JoinCallback;
import vproxy.util.Utils;

public class LoadOp implements MainOp {
    @Override
    public String key() {
        return "load";
    }

    @Override
    public int argCount() {
        return 1;
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public int pre(MainCtx ctx, String[] args) {
        if (ctx.get("noLoad", false)) {
            System.err.println("load and noLoadLast cannot be set together");
            return 1;
        }
        ctx.set("loaded", true);
        return 0;
    }

    @Override
    public int execute(MainCtx ctx, String[] args) {
        JoinCallback<String, Throwable> cb = new JoinCallback<>(new Main.CallbackInMain());
        try {
            Shutdown.load(args[0], cb);
        } catch (Exception e) {
            System.err.println("got exception when do pre-loading: " + Utils.formatErr(e));
            return 1;
        }
        cb.join();
        return 0;
    }
}
