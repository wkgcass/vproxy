package io.vproxy.app.app.args;

import io.vproxy.app.app.MainCtx;
import io.vproxy.app.app.MainOp;
import io.vproxy.base.Config;

public class NoSaveOp implements MainOp {
    @Override
    public String key() {
        return "noSave";
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
        if (ctx.get("autoSaveFileExists", false)) {
            System.err.println("noSave and autoSaveFile cannot be set together");
            return 1;
        }
        ctx.set("noSave", true);
        return 0;
    }

    @Override
    public int execute(MainCtx ctx, String[] args) {
        Config.configSavingDisabled = true;
        return 0;
    }
}
