package vproxy.app;

import vproxy.app.args.*;
import vproxy.component.app.Shutdown;
import vproxy.component.app.StdIOController;
import vproxy.dns.Resolver;
import vproxy.util.Callback;
import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.Utils;
import vproxyx.Daemon;
import vproxyx.Simple;
import vproxyx.WebSocksProxyAgent;
import vproxyx.WebSocksProxyServer;

import java.io.File;
import java.io.IOException;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String _HELP_STR_ = "" +
        "vproxy: usage java " + Main.class.getName() + " \\" +
        "\n\t\thelp                                         Show this message" +
        "\n" +
        "\n\t\tversion                                      Show version" +
        "\n" +
        "\n\t\tload ${filename}                             Load configuration from file" +
        "\n" +
        "\n\t\tcheck                                        check and exit" +
        "\n" +
        "\n\t\tresp-controller ${address} ${password}       Start the resp-controller, will" +
        "\n\t\t                                             be named as `resp-controller`" +
        "\n\t\thttp-controller ${address}                   Start the http-controller, will" +
        "\n\t\t                                             be named as `http-controller`" +
        "\n\t\tallowSystemCallInNonStdIOController          Allow system call in all controllers" +
        "\n" +
        "\n\t\tnoStdIOController                            StdIOController will not start" +
        "\n\t\t                                             if the flag is set" +
        "\n\t\tsigIntDirectlyShutdown                       Directly shutdown when got sig int" +
        "\n" +
        "\n\t\tdiscoveryConfig ${filename}                  Specify discovery config file" +
        "\n" +
        "\n\t\tpidFile                                      Set the pid file path" +
        "\n" +
        "\n\t\tnoLoadLast                                   Do not load last config on start up" +
        "\n" +
        "\n\t\tnoSave                                       Disable the ability to save config" +
        "\n" +
        "\n\t\tnoStartupBindCheck                           Disable bind check when loading config" +
        "\n\t\t                                             when launching. Will be automatically" +
        "\n\t\t                                             added when reloading using Systemd module" +
        "\n\t\tautoSaveFile ${filename}                     File path for auto saving" +
        "";
    private static boolean exitAfterLoading = false;

    private static void beforeStart() {
        Security.setProperty("networkaddress.cache.ttl", "0");
        Resolver.getDefault();
    }

    private static void runApp(String appClass, String[] args) {
        try {
            switch (appClass) {
                case "WebSocksProxyAgent":
                    WebSocksProxyAgent.main0(args);
                    break;
                case "WebSocksProxyServer":
                    WebSocksProxyServer.main0(args);
                    break;
                case "Simple":
                    Application.create();
                    Simple.main0(args);
                    break;
                case "Daemon":
                    Daemon.main0(args);
                    break;
                default:
                    System.err.println("unknown AppClass: " + appClass);
                    System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String[] checkFlagDeployInArguments(String[] args) {
        if (System.getProperty("eploy") != null) {
            // do not modify if -Deploy is already set
            return args;
        }
        List<String> returnArgs = new ArrayList<>(args.length);
        boolean found = false;
        for (final var arg : args) {
            if (arg.startsWith("-Deploy=")) {
                if (found) {
                    // should only appear once
                    throw new IllegalArgumentException("Cannot set multiple -Deploy= to run.");
                }
                found = true;
            } else if (arg.startsWith("-D")) {
                // other properties can be set freely
                var kv = arg.substring("-D".length());
                if (kv.contains("=")) {
                    var k = kv.substring(0, kv.indexOf("=")).trim();
                    var v = kv.substring(kv.indexOf("=") + "=".length()).trim();
                    if (!k.isEmpty() && !v.isEmpty()) {
                        System.setProperty(k, v);
                        continue;
                    }
                }
                returnArgs.add(arg);
            } else {
                returnArgs.add(arg);
            }
        }
        //noinspection ToArrayCallWithZeroLengthArrayArgument
        return returnArgs.toArray(new String[returnArgs.size()]);
    }

    public static void main(String[] args) {
        args = checkFlagDeployInArguments(args);
        beforeStart();

        // check for system properties and may run an app
        // apps can be found in vproxyx package
        String appClass = Config.appClass;
        if (appClass != null) {
            runApp(appClass, args);
            return;
        }

        try {
            Application.create();
        } catch (IOException e) {
            System.err.println("start application failed! " + e);
            e.printStackTrace();
            System.exit(1);
            return;
        }
        // init the address updater (should be called after Application initiates)
        ServerAddressUpdater.init();
        // start ControlEventLoop
        Application.get().controlEventLoop.loop();

        // every other thing should start after the loop

        // init ctx
        MainCtx ctx = new MainCtx();
        ctx.addOp(new AllowSystemCallInNonStdIOControllerOp());
        ctx.addOp(new CheckOp());
        ctx.addOp(new DiscoveryConfigOp());
        ctx.addOp(new HttpControllerOp());
        ctx.addOp(new LoadOp());
        ctx.addOp(new NoLoadLastOp());
        ctx.addOp(new NoSaveOp());
        ctx.addOp(new NoStartupBindCheckOp());
        ctx.addOp(new NoStdIOControllerOp());
        ctx.addOp(new PidFileOp());
        ctx.addOp(new RespControllerOp());
        ctx.addOp(new SigIntDirectlyShutdownOp());
        ctx.addOp(new AutoSaveFileOp());

        // load config if specified in args
        for (int i = 0; i < args.length; ++i) {
            String arg = args[i];
            String next = i + 1 < args.length ? args[i + 1] : null;
            String next2 = i + 2 < args.length ? args[i + 2] : null;
            switch (arg) {
                case "version":
                    System.out.println(Application.get().version);
                    System.exit(0);
                    return;
                case "help":
                    System.out.println(_HELP_STR_);
                    System.exit(0);
                    return;
                default:
                    var op = ctx.seekOp(arg);
                    if (op == null) {
                        System.err.println("unknown argument `" + arg + "`");
                        System.exit(1);
                        return;
                    }
                    var cnt = op.argCount();
                    assert cnt <= 2; // make it simple for now...
                    if (cnt == 0) {
                        ctx.addTodo(op, new String[0]);
                    } else {
                        // check next
                        if (next == null) {
                            System.err.println("`" + arg + "` expects more arguments");
                            System.exit(1);
                            return;
                        }
                        if (cnt == 1) {
                            ctx.addTodo(op, new String[]{next});
                            ++i;
                        } else {
                            assert cnt == 2;
                            if (next2 == null) {
                                System.err.println("`" + arg + "` expects more arguments");
                                System.exit(1);
                                return;
                            }
                            ctx.addTodo(op, new String[]{next, next2});
                            i += 2;
                        }
                    }
            }
        }
        ctx.executeAll();

        if (!ctx.get("loaded", false) && !Config.configLoadingDisabled) {
            File f = new File(Shutdown.defaultFilePath());
            if (f.exists()) {
                // load last config
                Logger.alert("trying to load from last saved config " + f.getAbsolutePath());
                Logger.alert("if the process fails to start, please manually remove " + f.getAbsolutePath() + " and start from scratch");
                if (ctx.get("isCheck", false)) {
                    exitAfterLoading = true;
                }
                try {
                    Shutdown.load(null, new CallbackInMain());
                } catch (Exception e) {
                    Logger.error(LogType.ALERT, "got exception when do pre-loading: " + Utils.formatErr(e));
                    System.exit(1);
                    return;
                }
            }
        }

        // write pid file
        if (!ctx.get("isCheck", false)) {
            try {
                Shutdown.writePid(ctx.get("pidFilePath", null));
            } catch (Exception e) {
                Logger.fatal(LogType.UNEXPECTED, "writing pid failed: " + Utils.formatErr(e));
                // failed on writing pid file is not a critical error
                // so we don't quit
            }
        }

        // exit if it's checking
        if (ctx.get("isCheck", false)) {
            if (!exitAfterLoading) {
                System.out.println("ok");
                System.exit(0);
                return;
            }
        }

        // start controllers

        if (!ctx.get("noStdIOController", false)) {
            // start stdioController
            StdIOController controller = new StdIOController();
            new Thread(controller::start, "StdIOControllerThread").start();
        }

        // run main app
        // init signal hooks
        Shutdown.initSignal();
        // start scheduled saving task
        Application.get().controlEventLoop.getSelectorEventLoop().period(60 * 60 * 1000, Main::saveConfig);
    }

    private static void saveConfig() {
        try {
            Shutdown.autoSave();
        } catch (Exception e) {
            Logger.shouldNotHappen("failed to save config", e);
        }
    }

    public static class CallbackInMain extends Callback<String, Throwable> {
        @Override
        protected void onSucceeded(String value) {
            Config.checkBind = true;
            if (exitAfterLoading) {
                System.out.println("ok");
                System.exit(0);
            }
        }

        @Override
        protected void onFailed(Throwable err) {
            System.err.println(Utils.formatErr(err));
            System.exit(1);
        }
    }
}
