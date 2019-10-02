package vproxy.app.args;

import vproxy.app.Config;
import vproxy.app.MainOp;
import vproxy.app.MainCtx;
import vproxy.app.mesh.DiscoveryConfigLoader;
import vproxy.util.Logger;

public class DiscoveryConfigOp implements MainOp {
    @Override
    public String key() {
        return "discoveryConfig";
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
        if (Config.discoveryConfigProvided) {
            System.err.println("discoveryConfig already set");
            return 1;
        }
        Config.discoveryConfigProvided = true;
        return 0;
    }

    @Override
    public int execute(MainCtx ctx, String[] args) {
        Logger.alert("loading discovery config from: " + args[0]);
        DiscoveryConfigLoader discoveryMain = DiscoveryConfigLoader.getInstance();
        int exitCode = discoveryMain.load(args[0]);
        if (exitCode != 0) {
            return exitCode;
        }
        exitCode = discoveryMain.check();
        if (exitCode != 0) {
            return exitCode;
        }
        exitCode = discoveryMain.gen();
        return exitCode;
    }
}
