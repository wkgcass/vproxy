package vproxy.app.app.args;

import vproxy.app.app.MainCtx;
import vproxy.app.app.MainOp;
import vproxy.base.Config;
import vproxy.base.connection.ServerSock;

public class NoStartupBindCheckOp implements MainOp {
    @Override
    public String key() {
        return "noStartupBindCheck";
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
        // check reuseport
        if (!ServerSock.supportReusePort()) {
            System.err.println("`noBindCheck` cannot be set because REUSEPORT is not supported");
            return 1;
        }
        if (ctx.get("noLoad", false)) {
            System.err.println("noStartupBindCheck and noLoadLast cannot be set together");
            return 1;
        }
        ctx.set("noBindCheck", true);
        return 0;
    }

    @Override
    public int execute(MainCtx ctx, String[] args) {
        Config.checkBind = false;
        return 0;
    }
}
