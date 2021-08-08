package vproxy.app.app.args;

import vproxy.app.app.Application;
import vproxy.app.app.MainCtx;
import vproxy.app.app.MainOp;
import vproxy.app.app.cmd.CmdResult;
import vproxy.app.app.cmd.SystemCommand;
import vproxy.base.util.callback.BlockCallback;

public class DockerNetworkPluginControllerOp implements MainOp {
    @Override
    public String key() {
        return "docker-network-plugin-controller";
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
        if (Application.get().dockerNetworkPluginControllerHolder.names().contains("docker-network-plugin-controller")) {
            System.err.println("docker-network-plugin-controller already set in startup arguments");
            return 1;
        }
        return 0;
    }

    @Override
    public int execute(MainCtx ctx, String[] args) {
        ctx.set("hasDockerNetworkPluginController", true);
        //noinspection StringBufferReplaceableByString
        StringBuilder call = new StringBuilder();
        call.append("System: add ")
            .append("docker-network-plugin-controller")
            .append(" (")
            .append("docker-network-plugin-controller")
            .append(") path ")
            .append(args[0]);
        BlockCallback<CmdResult, Throwable> cb = new BlockCallback<>();
        SystemCommand.handleSystemCommand(call.toString(), cb);
        try {
            cb.block();
        } catch (Throwable e) {
            System.err.println("start docker-network-plugin-controller on " + args[0] + " failed");
            e.printStackTrace();
            return 1;
        }
        return 0;
    }
}
