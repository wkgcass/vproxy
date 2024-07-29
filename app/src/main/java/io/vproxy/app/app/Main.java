package io.vproxy.app.app;

import io.vproxy.app.controller.StdIOController;
import io.vproxy.app.process.Shutdown;
import io.vproxy.app.vproxyx.Daemon;
import io.vproxy.app.vproxyx.DockerNetworkPluginControllerInit;
import io.vproxy.app.vproxyx.GenerateCommandDoc;
import io.vproxy.app.vproxyx.Simple;
import io.vproxy.base.Config;
import io.vproxy.base.dns.Resolver;
import io.vproxy.base.util.*;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.base.util.callback.JoinCallback;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.base.util.thread.VProxyThreadJsonParserCacheHolder;
import io.vproxy.pni.graal.GraalUtils;
import io.vproxy.r.org.graalvm.nativeimage.ImageInfoDelegate;
import io.vproxy.vfd.IPPort;
import io.vproxy.vproxyx.*;
import vjson.parser.ParserUtils;

import java.io.File;
import java.io.IOException;

public class Main {
    static final String _HELP_STR_ = "" +
        "vproxy: usage java " + Main.class.getName() + " \\" +
        "\n\t\thelp                                         Show this message" +
        "\n" +
        "\n\t\tversion                                      Show version" +
        "\n" +
        "\n\t\tload ${filename}                             Load configuration from file" +
        "\n" +
        "\n\t\tcheck                                        check and exit" +
        "\n" +
        "\n\t\tallowSystemCommandInNonStdIOController       Allow system cmd in all controllers" +
        "\n" +
        "\n\t\tnoStdIOController                            StdIOController will not start" +
        "\n\t\t                                             if the flag is set" +
        "\n\t\tsigIntDirectlyShutdown                       Directly shutdown when got sig int" +
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
        try {
            Utils.loadDynamicLibrary("pni");
        } catch (Throwable t) {
            Logger.warn(LogType.ALERT, "unable to load dynamic library: pni, native features cannot be used");
            if (ImageInfoDelegate.inImageCode() && OS.isWindows()) {
                Logger.warn(LogType.ALERT, "Tip: You may need MinGW UCRT64 (or libgcc_s_seh-1.dll,libwinpthread-1.dll) to make pni work");
            }
        }
        GraalUtils.init();
        ParserUtils.setParserCacheHolder(new VProxyThreadJsonParserCacheHolder());
        OOMHandler.handleOOM();

        {
            String gi = Utils.getSystemProperty("global_inspection");
            if (gi != null && !gi.equals("disable") && !gi.equals("disabled")) {
                IPPort ipport;
                try {
                    ipport = new IPPort(gi);
                } catch (IllegalArgumentException e) {
                    Logger.fatal(LogType.INVALID_EXTERNAL_DATA, "GlobalInspection should take an ip:port as the argument", e);
                    Utils.exit(1);
                    return;
                }
                try {
                    GlobalInspectionHttpServerLauncher.launch(ipport);
                } catch (IOException e) {
                    Logger.fatal(LogType.ALERT, "launching global inspection http server failed", e);
                    Utils.exit(1);
                    return;
                }
            }
        }

        Resolver.getDefault();
    }

    private static void runApp(String appClass, String[] args) {
        try {
            switch (appClass) {
                case "WebSocksProxyAgent":
                case "WebSocksAgent":
                case "wsagent":
                    WebSocksProxyAgent.main0(args);
                    break;
                case "WebSocksProxyServer":
                case "WebSocksServer":
                case "wsserver":
                    WebSocksProxyServer.main0(args);
                    break;
                case "KcpTun":
                case "kcptun":
                    KcpTun.main0(args);
                    break;
                case "Simple":
                case "simple":
                    Application.create();
                    Simple.main0(args);
                    break;
                case "Daemon":
                case "daemon":
                    Daemon.main0(args);
                    break;
                case "HelloWorld":
                case "helloworld":
                    HelloWorld.main0(args);
                    break;
                case "GenerateCommandDoc":
                    GenerateCommandDoc.main0(args);
                    break;
                case "PacketFilterGenerator":
                    PacketFilterGenerator.main0(args);
                    break;
                case "DockerNetworkPlugin":
                case "DockerPlugin":
                case "docker-plugin":
                case "dockerplugin":
                    DockerNetworkPluginControllerInit.main0(args);
                    break;
                case "ProxyNexus":
                case "proxy-nexus":
                case "proxynexus":
                    ProxyNexus.main0(args);
                    break;
                case "UOTWrapper":
                case "uot-wrapper":
                case "uot":
                case "uotwrapper":
                    UOTWrapper.main0(args);
                    break;
                default:
                    System.err.println("unknown AppClass: " + appClass);
                    Utils.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Utils.exit(1);
        }
    }

    public static void main(String[] args) {
        args = MainUtils.checkFlagDeployInArguments(args);
        beforeStart();

        // check for system properties and may run an app
        // apps can be found in vproxyx package
        String appClass = Config.appClass;
        if (appClass != null) {
            runApp(appClass, args);
            boolean exit = true;
            if (DockerNetworkPluginControllerInit.isInitiated()) {
                Logger.alert("Launch with docker network plugin controller");
                exit = false;
                args = DockerNetworkPluginControllerInit.getRebuiltArgs();
            }
            if (exit) {
                return;
            }
        }

        try {
            Application.create();
        } catch (IOException e) {
            System.err.println("start application failed! " + e);
            e.printStackTrace();
            Utils.exit(1);
            return;
        }
        // init the address updater (should be called after Application initiates)
        ServerAddressUpdater.init();

        // every other thing should start after the loop

        // init ctx
        MainCtx ctx = MainCtxHelper.buildDefault();
        if (!MainCtxHelper.defaultFillArguments(ctx, args)) {
            return;
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
                JoinCallback<String, Throwable> cb = new JoinCallback<>(new CallbackInMain());
                try {
                    Shutdown.load(null, cb);
                } catch (Exception e) {
                    Logger.error(LogType.ALERT, "got exception when do pre-loading: " + Utils.formatErr(e));
                    Utils.exit(1);
                    return;
                }
                cb.join();
            }
        }

        // write pid file
        if (!ctx.get("isCheck", false)) {
            try {
                Shutdown.writePid(ctx.get("pidFilePath", null));
            } catch (Exception e) {
                Logger.fatal(LogType.FILE_ERROR, "writing pid failed: " + Utils.formatErr(e));
                // failed on writing pid file is not a critical error
                // so we don't quit
            }
        }

        // handle docker-network-plugin-controller
        if (DockerNetworkPluginControllerInit.isInitiated()) {
            try {
                DockerNetworkPluginControllerInit.start();
            } catch (Exception e) {
                Logger.fatal(LogType.ALERT, "failed starting docker-network-plugin-controller", e);
                System.exit(1);
                return;
            }
        }

        // exit if it's checking
        if (ctx.get("isCheck", false)) {
            if (!exitAfterLoading) {
                System.out.println("ok");
                Utils.exit(0);
                return;
            }
        }

        // start controllers

        if (!ctx.get("noStdIOController", false)) {
            // start stdioController
            StdIOController controller = new StdIOController();
            VProxyThread.create(controller::start, "StdIOControllerThread").start();
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
                Utils.exit(0);
            }
        }

        @Override
        protected void onFailed(Throwable err) {
            System.err.println(Utils.formatErr(err));
            Utils.exit(1);
        }
    }
}
