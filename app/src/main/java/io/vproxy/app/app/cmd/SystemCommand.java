package io.vproxy.app.app.cmd;

import io.vproxy.app.controller.StdIOController;
import io.vproxy.app.process.Shutdown;
import io.vproxy.base.dns.Resolver;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.base.util.exception.XException;
import io.vproxy.vfd.IP;
import io.vproxy.vmirror.Mirror;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SystemCommand {
    private SystemCommand() {
    }

    static final String systemCommandHelpStr = "vproxy system commands:" +
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
        "\n                           [arguments ${xx,yy,zz,...}]" +
        "\n        System: list-detail plugin                 check plugins" +
        "\n        System: update plugin ${alias}             enable or disable a plugin" +
        "\n                              {enable|disable}" +
        "\n        System: remove plugin ${alias}             destroy a plugin" +
        "\n        System: list config                        show current config" +
        "\n        System: lookup ${domain}                   resolve v4/v6 ip for the domain" +
        "\n        System: mirror <${config-file}|disable>    load mirror config or disable mirror";

    public static boolean allowNonStdIOController = false;

    public static boolean isSystemCommand(String line) {
        return line.startsWith("System:");
    }

    public static void handleSystemCommand(String line, Callback<CmdResult, Throwable> cb) {
        String from = Utils.stackTraceStartingFromThisMethodInclusive()[1].getClassName();
        String cmd = line.substring("System:".length()).trim();
        switch (cmd) {
            case "help":
                String helpStr = systemCommandHelpStr;
                List<String> helpStrLines = Arrays.asList(helpStr.split("\n"));
                cb.succeeded(new CmdResult(helpStr, helpStrLines, helpStr));
                return;
            case "shutdown":
                if (!from.equals(StdIOController.class.getName())) {
                    cb.failed(new XException("you can only call shutdown via StdIOController"));
                    return;
                }
                Shutdown.shutdown();
                cb.succeeded(new CmdResult());
                return;
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
        } else if (cmd.startsWith("lookup ")) {
            executeLookup(cmd, cb);
            return;
        } else if (cmd.startsWith("mirror ")) {
            executeMirror(cmd, cb);
            return;
        }

        // run standard format commands
        Command command;
        try {
            command = Command.parseStrCmd(cmd);
        } catch (Exception e) {
            cb.failed(e);
            return;
        }
        command.run(SystemCommands.Companion.getInstance(), cb);
    }

    private static void executeLookup(String cmd, Callback<CmdResult, Throwable> cb) {
        String[] split = cmd.split(" ");
        if (split.length != 2) {
            cb.failed(new Exception("invalid system cmd for `lookup`: should specify one domain to resolve"));
            return;
        }
        String domain = split[1].trim();
        Resolver.getDefault().resolveV4(domain, Callback.ofFunction((err4, ipv4) ->
            Resolver.getDefault().resolveV6(domain, Callback.ofFunction((err6, ipv6) -> {
                if (ipv4 == null && ipv6 == null) {
                    cb.failed(new Exception("neither v4 nor v6 resolved"));
                } else {
                    List<IP> result = new ArrayList<>();
                    StringBuilder sb = new StringBuilder();
                    if (ipv4 != null) {
                        result.add(ipv4);
                        sb.append(ipv4.formatToIPString());
                    }
                    if (ipv6 != null) {
                        result.add(ipv6);
                        if (sb.length() != 0) {
                            sb.append("\n");
                        }
                        sb.append(ipv6.formatToIPStringWithoutBrackets());
                    }
                    cb.succeeded(new CmdResult(result, result, sb.toString()));
                }
            }))));
    }

    private static void executeMirror(String cmd, Callback<CmdResult, Throwable> cb) {
        var split = cmd.split(" ");
        if (split.length != 2) {
            cb.failed(new Exception("invalid system cmd for `mirror`: must specify mirror config path or use `disable`"));
            return;
        }
        var path = split[1].trim();
        if (path.equals("disable")) {
            Mirror.destroy();
            Logger.warn(LogType.ALERT, "mirror disabled");
            cb.succeeded(new CmdResult());
            return;
        }
        boolean ok;
        try {
            ok = Mirror.loadConfig(Files.readString(Path.of(Utils.filename(path))));
        } catch (IOException e) {
            cb.failed(e);
            return;
        }
        if (ok) {
            cb.succeeded(new CmdResult());
            return;
        }
        cb.failed(new Exception("loading mirror config from " + path + " failed"));
    }
}
