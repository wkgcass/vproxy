package io.vproxy.app.app.args;

import io.vproxy.app.app.MainCtx;
import io.vproxy.app.app.MainOp;
import io.vproxy.base.Config;
import io.vproxy.base.util.Utils;

import java.io.File;
import java.io.IOException;

public class AutoSaveFileOp implements MainOp {
    @Override
    public String key() {
        return "autoSaveFile";
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
        if (ctx.get("noSave", false)) {
            System.err.println("autoSaveFile and noSave cannot be set together");
            return 1;
        }
        if (ctx.get("autoSaveFileExists", false)) {
            System.err.println("autoSaveFile already set");
            return 1;
        }
        ctx.set("autoSaveFileExists", true);

        var path = Utils.filename(args[0]);
        var file = new File(path);
        if (file.exists()) {
            if (file.isFile()) {
                return 0;
            } else {
                System.err.println(path + " is not a file");
                return 1;
            }
        } else {
            try {
                //noinspection ResultOfMethodCallIgnored
                file.createNewFile();
                return 0;
            } catch (IOException e) {
                System.err.println("creating file " + path + " failed");
                e.printStackTrace();
                return 1;
            }
        }
    }

    @Override
    public int execute(MainCtx ctx, String[] args) {
        Config.autoSaveFilePath = args[0];
        return 0;
    }
}
