package vproxy.app.cmd;

import vproxy.app.Application;
import vproxy.app.RESPControllerHolder;
import vproxy.app.cmd.handle.param.AddrHandle;
import vproxy.component.app.RESPController;
import vproxy.component.app.Shutdown;
import vproxy.component.app.StdIOController;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.NotFoundException;
import vproxy.component.exception.XException;
import vproxy.util.Callback;
import vproxy.util.Logger;
import vproxy.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class SystemCommand {
    private SystemCommand() {
    }

    static final String systemCallHelpStr = "" +
        "\n        System call: help                          show this message" +
        "\n        System call: shutdown                      shutdown the vproxy process" +
        "\n        System call: load ${filepath}              load config commands from a file" +
        "\n        System call: save ${filepath}              save current config into a file" +
        "\n        System call: add resp-controller           start resp controller" +
        "\n                               ${alias}" +
        "\n                               address  ${bind addr}" +
        "\n                               password ${password}" +
        "\n        System call: remove resp-controller        stop resp controller" +
        "\n                               ${name}" +
        "\n        System call: list-detail resp-controller   check resp controller" +
        "\n        System call: list config                   show current config";

    public static boolean allowNonStdIOController = false;

    public static boolean isSystemCall(String line) {
        return line.startsWith("System call:");
    }

    public static void handleSystemCall(String line, Callback<CmdResult, ? super XException> cb) {
        String from = Thread.currentThread().getStackTrace()[2].getClassName();
        String cmd = line.substring("System call:".length()).trim();
        outswitch:
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
            default:
                if (cmd.startsWith("load ")) {
                    if (!from.equals(StdIOController.class.getName())) {
                        cb.failed(new XException("you can only call load via StdIOController"));
                        break;
                    }
                    String[] split = cmd.split(" ");
                    if (split.length <= 1) {
                        cb.failed(new XException("invalid system call for `load`: should specify a file name to load"));
                        break;
                    }
                    StringBuilder filename = new StringBuilder();
                    for (int i = 1; i < split.length; ++i) {
                        if (i != 1) {
                            filename.append(" ");
                        }
                        filename.append(split[i]);
                    }
                    try {
                        Shutdown.load(filename.toString(), new Callback<String, Throwable>() {
                            @Override
                            protected void onSucceeded(String value) {
                                cb.succeeded(new CmdResult());
                            }

                            @Override
                            protected void onFailed(Throwable err) {
                                cb.failed(new XException(err.toString()));
                            }
                        });
                    } catch (Exception e) {
                        cb.failed(new XException("got exception when do pre-loading: " + Utils.formatErr(e)));
                    }
                    break;
                } else if (cmd.startsWith("save ")) {
                    if (!from.equals(StdIOController.class.getName())) {
                        cb.failed(new XException("you can only call save via StdIOController"));
                        break;
                    }
                    String[] split = cmd.split(" ");
                    if (split.length <= 1) {
                        cb.failed(new XException("invalid system call for `save`: should specify a file name to save"));
                        break;
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
                        cb.failed(new XException("got exception when saving: " + Utils.formatErr(e)));
                    }
                    cb.succeeded(new CmdResult());
                    break;
                } else if (cmd.startsWith("add ")) {
                    String[] arr = cmd.split(" ");
                    if (arr.length < 2) {
                        cb.failed(new XException("invalid add command"));
                        break;
                    }
                    switch (arr[1]) {
                        case "resp-controller":
                            if (arr.length == 7) {
                                handleAddRespController(arr, cb);
                                break outswitch;
                            }
                    }
                } else if (cmd.startsWith("remove ")) {
                    String[] arr = cmd.split(" ");
                    if (arr.length < 2) {
                        cb.failed(new XException("invalid remove command"));
                        break;
                    }
                    switch (arr[1]) {
                        case "resp-controller":
                            if (arr.length == 3) {
                                handleRemoveController(arr, cb);
                                break outswitch;
                            }
                    }
                } else if (cmd.startsWith("list ")) {
                    String[] arr = cmd.split(" ");
                    if (arr.length < 2) {
                        cb.failed(new XException("invalid list command"));
                        break;
                    }
                    switch (arr[1]) {
                        case "resp-controller":
                            if (arr.length == 2) {
                                handleListController(false, cb);
                                break outswitch;
                            }
                        case "config":
                            if (arr.length == 2) {
                                handleListConfig(cb);
                                break outswitch;
                            }
                    }
                } else if (cmd.startsWith("list-detail ")) {
                    String[] arr = cmd.split(" ");
                    if (arr.length < 2) {
                        cb.failed(new XException("invalid list-detail command"));
                        break;
                    }
                    switch (arr[1]) {
                        case "resp-controller":
                            if (arr.length == 2) {
                                handleListController(true, cb);
                                break outswitch;
                            }
                    }
                }
                cb.failed(new XException("unknown or invalid system call `" + cmd + "`"));
        }
    }

    private static void handleListConfig(Callback<CmdResult, ? super XException> cb) {
        String config = Shutdown.currentConfig();
        List<String> lines = Arrays.asList(config.split("\n"));
        cb.succeeded(new CmdResult(config, lines, config));
    }

    private static void handleAddRespController(String[] arr, Callback<CmdResult, ? super XException> cb) {
        Command cmd;
        try {
            cmd = Command.statm(Arrays.asList(arr));
        } catch (Exception e) {
            cb.failed(new XException("invalid system call: " + Utils.formatErr(e)));
            return;
        }
        if (!cmd.args.containsKey(Param.addr)) {
            cb.failed(new XException("missing address"));
            return;
        }
        if (!cmd.args.containsKey(Param.pass)) {
            cb.failed(new XException("missing password"));
            return;
        }
        try {
            AddrHandle.check(cmd);
        } catch (Exception e) {
            cb.failed(new XException("invalid system call"));
            return;
        }

        InetSocketAddress addr;
        try {
            addr = AddrHandle.get(cmd);
        } catch (Exception e) {
            Logger.shouldNotHappen("it should have already been checked but still failed", e);
            cb.failed(new XException("invalid system call"));
            return;
        }
        byte[] pass = cmd.args.get(Param.pass).getBytes();

        // start
        try {
            Application.get().respControllerHolder.add(cmd.resource.alias, addr, pass);
        } catch (AlreadyExistException e) {
            cb.failed(new XException("the RESPController is already started"));
        } catch (IOException e) {
            cb.failed(new XException("got exception when starting RESPController: " + Utils.formatErr(e)));
        }
        cb.succeeded(new CmdResult());
    }

    private static void handleRemoveController(String[] arr, Callback<CmdResult, ? super XException> cb) {
        try {
            Application.get().respControllerHolder.removeAndStop(arr[2]);
        } catch (NotFoundException e) {
            cb.failed(new XException("not found"));
        }
        cb.succeeded(new CmdResult());
    }

    private static void handleListController(boolean detail, Callback<CmdResult, ? super XException> cb) {
        RESPControllerHolder h = Application.get().respControllerHolder;
        List<String> names = h.names();
        List<RESPController> controllers = new LinkedList<>();
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (String name : names) {
            if (isFirst) isFirst = false;
            else sb.append("\n");
            RESPController c;
            try {
                c = h.get(name);
            } catch (NotFoundException e) {
                // should not happen if no concurrency. just ignore
                continue;
            }
            controllers.add(c);
            sb.append(c.alias);
            if (detail) {
                sb.append(" -> ").append(c.server.id());
            }
        }
        String resps = sb.toString();
        List<String> lines = Arrays.asList(resps.split("\n"));
        cb.succeeded(new CmdResult(controllers, lines, resps));
    }
}
