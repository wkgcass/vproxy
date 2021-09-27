package io.vproxy.app.vproxyx;

import io.vproxy.app.controller.DockerNetworkDriver;
import io.vproxy.app.app.Application;
import io.vproxy.app.app.MainCtx;
import io.vproxy.app.app.MainCtxHelper;
import io.vproxy.app.controller.DockerNetworkDriver;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.OS;
import io.vproxy.base.util.Utils;
import io.vproxy.vfd.UDSPath;

import java.io.File;
import java.nio.file.Files;

public class DockerNetworkPluginControllerInit {
    private static final String vproxySock = "/var/run/docker/plugins/vproxy.sock";
    private static boolean initiated = false;
    private static String[] args;

    private static final String DEFAULT_TEMPORARY_CONFIG = "" +
        "System: add resp-controller resp-controller address sock:/var/run/docker/vproxy.sock password docker\n";
    private static boolean isFirstLaunch = false;

    public static void main0(String[] args) throws Exception {
        checkOSVersion();

        MainCtx ctx = MainCtxHelper.buildDefault();
        ctx.removeOp("load");
        ctx.removeOp("noLoadLast");
        ctx.removeOp("noSave");
        ctx.removeOp("autoSaveFile");

        if (!MainCtxHelper.defaultFillArguments(ctx, args)) {
            return;
        }

        initiated = true;
        // add load and autoSaveFile
        String[] retArgs = new String[args.length + 4];
        System.arraycopy(args, 0, retArgs, 0, args.length);
        retArgs[retArgs.length - 4] = "load";
        retArgs[retArgs.length - 3] = DockerNetworkDriver.TEMPORARY_CONFIG_FILE;
        retArgs[retArgs.length - 2] = "autoSaveFile";
        retArgs[retArgs.length - 1] = DockerNetworkDriver.TEMPORARY_CONFIG_FILE;
        DockerNetworkPluginControllerInit.args = retArgs;

        File configFile = new File(DockerNetworkDriver.TEMPORARY_CONFIG_FILE);
        if (configFile.exists()) {
            // check whether nics require initializing
            // if the nics already initialized, at lease one xdp should be added
            String config = Files.readString(configFile.toPath());
            String[] lines = config.split("\n");
            boolean ifaceInitiated = false;
            for (String line : lines) {
                if (line.startsWith("add xdp ")) {
                    ifaceInitiated = true;
                    break;
                }
            }
            if (!ifaceInitiated) {
                isFirstLaunch = true;
            }
        } else {
            File configDir = configFile.getParentFile();
            if (!configDir.exists()) {
                Files.createDirectories(configDir.toPath());
            }

            File scriptFile = new File(DockerNetworkDriver.PERSISTENT_SCRIPT);
            if (scriptFile.exists()) {
                if (!scriptFile.setExecutable(true)) {
                    throw new Exception("setting executable on " + scriptFile.getAbsolutePath() + " failed");
                }
                ProcessBuilder pb = new ProcessBuilder(scriptFile.getAbsolutePath());
                Utils.execute(pb, 5_000);
            }
            File persistFile = new File(DockerNetworkDriver.PERSISTENT_CONFIG_FILE);
            if (persistFile.exists()) {
                Files.copy(persistFile.toPath(), configFile.toPath());
            } else {
                isFirstLaunch = true;
                Files.writeString(configFile.toPath(), DEFAULT_TEMPORARY_CONFIG);
            }
        }
    }

    private static void checkOSVersion() throws Exception {
        if (!OS.isLinux()) {
            throw new Exception("Current OS is not Linux, cannot run docker network plugin");
        }

        int major = OS.major();
        int minor = OS.minor();
        if (major < 0 || minor < 0) {
            Logger.warn(LogType.ALERT, "Unable to parse kernel version: " + OS.version());
            return;
        }
        if (major < 5) {
            throw new Exception("Current kernel version is not supported: " + major + "." + minor + ", requires at least 5.4, 5.10 is recommended");
        }
        if (major == 5 && minor < 4) {
            throw new Exception("Current kernel version is not supported: " + major + "." + minor + ", requires at least 5.4, 5.10 is recommended");
        }
    }

    public static void start() throws Exception {
        Application.get().dockerNetworkPluginControllerHolder.create(new UDSPath(vproxySock), isFirstLaunch);
    }

    public static boolean isInitiated() {
        return initiated;
    }

    public static String[] getRebuiltArgs() {
        return args;
    }
}
