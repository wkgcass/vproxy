package vproxy.app;

import vproxy.app.cmd.CmdResult;
import vproxy.app.cmd.SystemCommand;
import vproxy.app.mesh.DiscoveryConfigLoader;
import vproxy.component.app.Shutdown;
import vproxy.component.app.StdIOController;
import vproxy.component.exception.XException;
import vproxy.dns.Resolver;
import vproxy.util.*;
import vproxyx.Simple;
import vproxyx.WebSocksProxyAgent;
import vproxyx.WebSocksProxyServer;

import java.io.File;
import java.io.IOException;
import java.security.Security;

public class Main {
    private static final String _HELP_STR_ = "" +
        "vproxy: usage java " + Main.class.getName() + " \\" +
        "\n\t\thelp                                         Show this message" +
        "\n" +
        "\n\t\tload ${filename}                             Load configuration from file" +
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
        "";

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
                default:
                    System.err.println("unknown AppClass: " + appClass);
                    System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void main(String[] args) {
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

        // load config if specified in args
        boolean loaded = false;
        boolean noStdIOController = false;
        String pidFilePath = null;
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
                case "load":
                    // try load after all other configs are processed
                    break;
                case "resp-controller":
                case "http-controller":
                    if (next == null || (arg.equals("resp-controller") && next2 == null)) {
                        System.err.println("invalid system call for `" + arg + "`: should specify an address"
                            + (arg.equals("resp-controller") ? " and a password" : ""));
                        System.exit(1);
                        return;
                    }
                    // handle controller, so increase the cursor
                    i += (arg.equals("resp-controller") ? 2 : 1);

                    StringBuilder call = new StringBuilder();
                    call.append("System call: add ").append(arg).append(" (").append(arg).append(") address ").append(next);
                    if (arg.equals("resp-controller")) {
                        call.append(" password ").append(next2);
                    }
                    BlockCallback<CmdResult, XException> cb = new BlockCallback<>();
                    SystemCommand.handleSystemCall(call.toString(), cb);
                    try {
                        cb.block();
                    } catch (XException e) {
                        System.err.println("start " + arg + " failed");
                        e.printStackTrace();
                        return;
                    }
                    break;
                case "allowSystemCallInNonStdIOController":
                    SystemCommand.allowNonStdIOController = true;
                    break;
                case "noStdIOController":
                    noStdIOController = true;
                    break;
                case "sigIntDirectlyShutdown":
                    Shutdown.sigIntBeforeTerminate = 1;
                    break;
                case "discoveryConfig":
                    if (next == null) {
                        System.err.println("config file path required");
                        System.exit(1);
                        return;
                    }
                    System.out.println("loading discovery config from: " + next);
                    // handle config, so increase the cursor
                    ++i;
                    DiscoveryConfigLoader discoveryMain = DiscoveryConfigLoader.getInstance();
                    int exitCode = discoveryMain.load(next);
                    if (exitCode != 0) {
                        System.exit(exitCode);
                        return;
                    }
                    exitCode = discoveryMain.check();
                    if (exitCode != 0) {
                        System.exit(exitCode);
                        return;
                    }
                    exitCode = discoveryMain.gen();
                    if (exitCode != 0) {
                        System.exit(exitCode);
                        return;
                    }
                    Config.discoveryConfigProvided = true;
                    break;
                case "pidFile":
                    if (next == null) {
                        System.err.println("pid file path should be specified");
                        System.exit(1);
                        return;
                    }
                    // handle pid file path, so increase the cursor
                    ++i;
                    pidFilePath = next;
                    break;
                case "noLoadLast":
                    loaded = true; // set this flag to true, then last config won't be loaded
                    break;
                case "noSave":
                    Config.configSavingDisabled = true;
                    break;
                default:
                    System.err.println("unknown argument `" + arg + "`");
                    System.exit(1);
                    return;
            }
        }
        for (int i = 0; i < args.length; ++i) {
            String arg = args[i];
            String next = i + 1 < args.length ? args[i + 1] : null;
            if ("load".equals(arg)) {
                loaded = true;
                // if error occurred, the program will exit
                // so set loaded flag here is ok
                if (next == null) {
                    System.err.println("invalid system call for `load`: should specify a file name to load");
                    System.exit(1);
                    return;
                }
                // handle load, so increase the cursor
                ++i;
                try {
                    Shutdown.load(next, new CallbackInMain());
                } catch (Exception e) {
                    System.err.println("got exception when do pre-loading: " + Utils.formatErr(e));
                    System.exit(1);
                    return;
                }
            }
        }
        if (!loaded && !Config.configLoadingDisabled) {
            File f = new File(Shutdown.defaultFilePath());
            if (f.exists()) {
                // load last config
                System.out.println("trying to load from last saved config " + f.getAbsolutePath());
                System.out.println("if the process fails to start, remove " + f.getAbsolutePath() + " and start from scratch");
                try {
                    Shutdown.load(null, new CallbackInMain());
                } catch (Exception e) {
                    System.err.println("got exception when do pre-loading: " + Utils.formatErr(e));
                }
            }
        }

        // write pid file
        try {
            Shutdown.writePid(pidFilePath);
        } catch (Exception e) {
            Logger.fatal(LogType.UNEXPECTED, "writing pid failed: " + Utils.formatErr(e));
            // failed on writing pid file is not a critical error
            // so we don't quit
        }

        // start controllers

        if (!noStdIOController) {
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
            Shutdown.save(null);
        } catch (Exception e) {
            Logger.shouldNotHappen("failed to save config", e);
        }
    }

    private static class CallbackInMain extends Callback<String, Throwable> {
        @Override
        protected void onSucceeded(String value) {
            // do nothing if succeeded
        }

        @Override
        protected void onFailed(Throwable err) {
            System.err.println(Utils.formatErr(err));
            System.exit(1);
        }
    }
}
