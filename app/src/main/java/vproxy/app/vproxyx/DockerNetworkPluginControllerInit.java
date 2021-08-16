package vproxy.app.vproxyx;

import vproxy.app.app.Application;
import vproxy.app.app.MainCtx;
import vproxy.app.app.MainCtxHelper;
import vproxy.app.controller.DockerNetworkDriver;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.base.util.OS;
import vproxy.vfd.UDSPath;

import java.io.File;
import java.nio.file.Files;

public class DockerNetworkPluginControllerInit {
    private static final String vproxySock = "/var/run/docker/plugins/vproxy.sock";
    private static boolean initiated = false;
    private static String[] args;

    private static final String DEFAULT_TEMPORARY_CONFIG = "" +
        "System: add resp-controller resp-controller address 127.0.0.1:16309 password docker\n";
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
            isFirstLaunch = true;
            File configDir = configFile.getParentFile();
            if (!configDir.exists()) {
                Files.createDirectories(configDir.toPath());
            }
            Files.writeString(configFile.toPath(), DEFAULT_TEMPORARY_CONFIG);
        }
    }

    private static void checkOSVersion() throws Exception {
        if (!OS.isLinux()) {
            throw new Exception("Current OS is not Linux, cannot run docker network plugin");
        }

        String version = OS.version();
        if (!version.contains(".")) {
            Logger.warn(LogType.ALERT, "Unable to parse kernel version: " + version);
            return;
        }
        String majorStr = version.substring(0, version.indexOf("."));
        String rest = version.substring(version.indexOf(".") + 1);
        if (!rest.contains(".")) {
            Logger.warn(LogType.ALERT, "Unable to parse kernel version, missing minor version: " + version);
            return;
        }
        String minorStr = rest.substring(0, rest.indexOf("."));
        int major;
        int minor;
        try {
            major = Integer.parseInt(majorStr);
            minor = Integer.parseInt(minorStr);
        } catch (NumberFormatException ignore) {
            Logger.warn(LogType.ALERT, "Unable to parse kernel version, major/minor not integer: " + version);
            return;
        }
        if (major < 5) {
            throw new Exception("Current kernel version is not supported: " + major + "." + minor + ", requires at least 5.10");
        }
        if (minor < 10) {
            throw new Exception("Current kernel version is not supported: " + major + "." + minor + ", requires at least 5.10");
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
