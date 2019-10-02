package vproxy.app.args;

import vproxy.app.Application;
import vproxy.app.MainOp;
import vproxy.app.MainCtx;
import vproxy.app.cmd.CmdResult;
import vproxy.app.cmd.SystemCommand;
import vproxy.component.exception.XException;
import vproxy.util.BlockCallback;

public class HttpControllerOp implements MainOp {
    @Override
    public String key() {
        return "http-controller";
    }

    @Override
    public int argCount() {
        return 1;
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public int pre(MainCtx ctx, String[] args) {
        if (Application.get().httpControllerHolder.names().contains("http-controller")) {
            System.err.println("http-controller already set in startup arguments");
            return 1;
        }
        return 0;
    }

    @Override
    public int execute(MainCtx ctx, String[] args) {
        //noinspection StringBufferReplaceableByString
        StringBuilder call = new StringBuilder();
        call.append("System call: add ")
            .append("http-controller")
            .append(" (")
            .append("http-controller")
            .append(") address ")
            .append(args[0]);
        BlockCallback<CmdResult, XException> cb = new BlockCallback<>();
        SystemCommand.handleSystemCall(call.toString(), cb);
        try {
            cb.block();
        } catch (XException e) {
            System.err.println("start http-controller failed");
            e.printStackTrace();
            return 1;
        }
        return 0;
    }
}
