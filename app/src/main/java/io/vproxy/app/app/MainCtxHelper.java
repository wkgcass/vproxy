package io.vproxy.app.app;

import io.vproxy.app.app.args.*;
import io.vproxy.base.util.Utils;

public class MainCtxHelper {
    private MainCtxHelper() {
    }

    public static MainCtx buildDefault() {
        MainCtx ctx = new MainCtx();
        ctx.addOp(new AllowSystemCommandInNonStdIOControllerOp());
        ctx.addOp(new CheckOp());
        ctx.addOp(new LoadOp());
        ctx.addOp(new NoLoadLastOp());
        ctx.addOp(new NoSaveOp());
        ctx.addOp(new NoStartupBindCheckOp());
        ctx.addOp(new NoStdIOControllerOp());
        ctx.addOp(new PidFileOp());
        ctx.addOp(new SigIntDirectlyShutdownOp());
        ctx.addOp(new AutoSaveFileOp());
        return ctx;
    }

    public static boolean defaultFillArguments(MainCtx ctx, String[] args) {
        for (int i = 0; i < args.length; ++i) {
            String arg = args[i];
            String next = i + 1 < args.length ? args[i + 1] : null;
            switch (arg) {
                case "version":
                    System.err.println(Application.get().version);
                    Utils.exit(0);
                    return false;
                case "help":
                    System.out.println(Main._HELP_STR_);
                    Utils.exit(0);
                    return false;
                default:
                    var op = ctx.seekOp(arg);
                    if (op == null) {
                        System.err.println("unknown argument `" + arg + "`");
                        Utils.exit(1);
                        return false;
                    }
                    var cnt = op.argCount();
                    assert cnt <= 1; // make it simple for now...
                    if (cnt == 0) {
                        ctx.addTodo(op, new String[0]);
                    } else {
                        // check next
                        if (next == null) {
                            System.err.println("`" + arg + "` expects more arguments");
                            Utils.exit(1);
                            return false;
                        }
                        if (cnt == 1) {
                            ctx.addTodo(op, new String[]{next});
                            ++i;
                        } else {
                            throw new Error("should not reach here");
                        }
                    }
            }
        }
        return true;
    }
}
