package vproxy.app.app.cmd;

import vproxy.app.controller.StdIOController;
import vproxy.app.process.Shutdown;
import vproxy.base.util.Utils;
import vproxy.base.util.callback.Callback;
import vproxy.base.util.exception.XException;

import java.util.Arrays;
import java.util.List;

public class SystemCommand {
    private SystemCommand() {
    }

    static final String systemCommandHelpStr = "" +
        "\n        System: help                               show this message" +
        "\n        System: shutdown                           shutdown the vproxy process" +
        "\n        System: load ${filepath}                   load config commands from a file" +
        "\n        System: save ${filepath}                   save current config into a file" +
        "\n        System: add resp-controller                start resp controller" +
        "\n                               ${alias}" +
        "\n                               address  ${bind addr}" +
        "\n                               password ${password}" +
        "\n        System: remove resp-controller             stop resp controller" +
        "\n                               ${alias}" +
        "\n        System: list-detail resp-controller        check resp controller" +
        "\n        System: add http-controller                start http controller" +
        "\n                               ${alias}" +
        "\n                               address ${bind addr}" +
        "\n        System: remove http-controller             stop http controller" +
        "\n                               ${alias}" +
        "\n        System: list-detail http-controller        check http controller" +
        "\n        System: add docker-network-plugin-controller              start docker net plugin ctl" +
        "\n                               ${alias}" +
        "\n                               path ${unix domain socket path}" +
        "\n        System: remove docker-network-plugin-controller           stop docker net plugin ctl" +
        "\n                               ${alias}" +
        "\n        System: list-detail docker-network-plugin-controller show docker net plugin ctl list" +
        "\n        System: add plugin ${alias}                load a plugin" +
        "\n                           url   ${url}" +
        "\n                           class ${classname}" +
        "\n        System: list-detail plugin                 check plugins" +
        "\n        System: update plugin ${alias}             enable or disable a plugin" +
        "\n                              {enable|disable}" +
        "\n        System: remove plugin ${alias}             destroy a plugin" +
        "\n        System: list config                        show current config";

    public static boolean allowNonStdIOController = false;
    private static final SystemCommands systemCommands = new SystemCommands();

    public static boolean isSystemCommand(String line) {
        return line.startsWith("System:");
    }

    public static void handleSystemCommand(String line, Callback<CmdResult, ? super Throwable> cb) {
        String from = Utils.stackTraceStartingFromThisMethodInclusive()[1].getClassName();
        String cmd = line.substring("System:".length()).trim();
        switch (cmd) {
            case "help":
                String helpStr = Command.helpString();
                List<String> helpStrLines = Arrays.asList(helpStr.split("\n"));
                cb.succeeded(new CmdResult(helpStr, helpStrLines, helpStr));
                break;
            case "shutdown":
                if (!from.equals(StdIOController.class.getName())) {
                    cb.failed(new XException("you can only call shutdown via StdIOController"));
                    break;
                }
                Shutdown.shutdown();
                cb.succeeded(new CmdResult());
                break;
        }
        if (cmd.startsWith("load ")) {
            if (!from.equals(StdIOController.class.getName())) {
                cb.failed(new XException("you can only call load via StdIOController"));
                return;
            }
            String[] split = cmd.split(" ");
            if (split.length <= 1) {
                cb.failed(new Exception("invalid system cmd for `load`: should specify a file name to load"));
                return;
            }
            StringBuilder filename = new StringBuilder();
            for (int i = 1; i < split.length; ++i) {
                if (i != 1) {
                    filename.append(" ");
                }
                filename.append(split[i]);
            }
            try {
                Shutdown.load(filename.toString(), new Callback<>() {
                    @Override
                    protected void onSucceeded(String value) {
                        cb.succeeded(new CmdResult());
                    }

                    @Override
                    protected void onFailed(Throwable err) {
                        cb.failed(err);
                    }
                });
            } catch (Exception e) {
                cb.failed(new Exception("got exception when do pre-loading: " + Utils.formatErr(e)));
            }
            return;
        } else if (cmd.startsWith("save ")) {
            if (!from.equals(StdIOController.class.getName())) {
                cb.failed(new XException("you can only call save via StdIOController"));
                return;
            }
            String[] split = cmd.split(" ");
            if (split.length <= 1) {
                cb.failed(new Exception("invalid system cmd for `save`: should specify a file name to save"));
                return;
            }
            StringBuilder filename = new StringBuilder();
            for (int i = 1; i < split.length; ++i) {
                if (i != 1) {
                    filename.append(" ");
                }
                filename.append(split[i]);
            }
            try {
                Shutdown.save(filename.toString());
            } catch (Exception e) {
                cb.failed(new Exception("got exception when saving: " + Utils.formatErr(e)));
            }
            cb.succeeded(new CmdResult());
            return;
        }

        // run standard format commands
        Command command;
        try {
            command = Command.statm(Arrays.asList(cmd.split(" ")));
        } catch (Exception e) {
            cb.failed(e);
            return;
        }
        CmdResult result;
        try {
            result = systemCommands.execute(command);
        } catch (Exception e) {
            cb.failed(e);
            return;
        }
        cb.succeeded(result);
    }
}
