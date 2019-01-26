package net.cassite.vproxy.component.app;

import net.cassite.vproxy.app.Application;
import net.cassite.vproxy.app.RESPControllerHolder;
import net.cassite.vproxy.app.cmd.CmdResult;
import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.app.cmd.Param;
import net.cassite.vproxy.app.cmd.handle.param.AddrHandle;
import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.util.Callback;
import net.cassite.vproxy.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class StdIOController {
    private static final String STARTER = "> ";

    @SuppressWarnings("InfiniteLoopStatement")
    public void start() {
        while (true) {
            Scanner scanner = new Scanner(System.in);
            printStarter();
            String line = scanner.nextLine().trim();
            if (line.isEmpty())
                continue;
            if (line.equals("h") || line.equals("help") || line.equals("man")) {
                stdout(Command.helpString());
                continue;
            }
            if (line.equals("v") || line.equals("version")) {
                stdout(Application.get().appVersion);
                continue;
            }
            if (line.startsWith("System call:")) {
                handleSystemCall(line);
            } else {
                handleCommand(line);
            }
        }
    }

    private static void printStarter() {
        System.out.print(STARTER);
    }

    private static void stdout(String msg) {
        System.out.println(msg);
    }

    private static void stderr(String err) {
        System.err.println(err);
    }

    private static void handleSystemCall(String line) {
        String cmd = line.substring("System call:".length()).trim();
        outswitch:
        switch (cmd) {
            case "help":
                stdout(Command.helpString());
                break;
            case "shutdown":
                Shutdown.shutdown();
                break;
            default:
                if (cmd.startsWith("load")) {
                    String[] split = cmd.split(" ");
                    if (split.length <= 1) {
                        stderr("invalid system call for `load`: should specify a file name to load");
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
                        Shutdown.load(filename.toString(), new StrResultCallback(line));
                    } catch (Exception e) {
                        stderr("got exception when do pre-loading: " + Utils.formatErr(e));
                    }
                    break;
                } else if (cmd.startsWith("add ")) {
                    String[] arr = cmd.split(" ");
                    if (arr.length < 2) {
                        stderr("invalid add command");
                        break;
                    }
                    switch (arr[1]) {
                        case "resp-controller":
                            if (arr.length == 7) {
                                handleAddRespController(arr);
                                break outswitch;
                            }
                    }
                } else if (cmd.startsWith("remove ")) {
                    String[] arr = cmd.split(" ");
                    if (arr.length < 2) {
                        stderr("invalid remove command");
                    }
                    switch (arr[1]) {
                        case "resp-controller":
                            if (arr.length == 3) {
                                handleRemoveController(arr);
                                break outswitch;
                            }
                    }
                } else if (cmd.startsWith("list ")) {
                    String[] arr = cmd.split(" ");
                    if (arr.length < 2) {
                        stderr("invalid list command");
                        break;
                    }
                    switch (arr[1]) {
                        case "resp-controller":
                            if (arr.length == 2) {
                                handleListController(false);
                                break outswitch;
                            }
                    }
                } else if (cmd.startsWith("list-detail ")) {
                    String[] arr = cmd.split(" ");
                    if (arr.length < 2) {
                        stderr("invalid list-detail command");
                        break;
                    }
                    switch (arr[1]) {
                        case "resp-controller":
                            if (arr.length == 2) {
                                handleListController(true);
                                break outswitch;
                            }
                    }
                }
                stderr("unknown or invalid system call `" + cmd + "`");
        }
    }

    private static void handleAddRespController(String[] arr) {
        Command cmd;
        try {
            cmd = Command.statm(Arrays.asList(arr));
        } catch (Exception e) {
            stderr("invalid system call: " + Utils.formatErr(e));
            return;
        }
        if (!cmd.args.containsKey(Param.addr)) {
            stderr("missing address");
            return;
        }
        if (!cmd.args.containsKey(Param.pass)) {
            stderr("missing password");
            return;
        }
        try {
            AddrHandle.check(cmd);
        } catch (Exception e) {
            stderr("invalid");
            return;
        }

        InetSocketAddress addr = AddrHandle.get(cmd);
        byte[] pass = cmd.args.get(Param.pass).getBytes();

        // start
        try {
            Application.get().respControllerHolder.add(cmd.resource.alias, addr, pass);
        } catch (AlreadyExistException e) {
            stderr("the RESPController is already started");
        } catch (IOException e) {
            stderr("got exception when starting RESPController: " + Utils.formatErr(e));
        }
        stdout("(done)");
    }

    private static void handleRemoveController(String[] arr) {
        try {
            Application.get().respControllerHolder.removeAndStop(arr[2]);
        } catch (NotFoundException e) {
            stderr("not found");
        }
        stdout("(done)");
    }

    private static void handleListController(boolean detail) {
        RESPControllerHolder h = Application.get().respControllerHolder;
        List<String> names = h.names();
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
            sb.append(c.alias);
            if (detail) {
                sb.append("\t").append(c.server.id());
            }
        }
        stdout(sb.toString());
    }

    private static void handleCommand(String line) {
        Command c;
        try {
            c = Command.parseStrCmd(line);
        } catch (Exception e) {
            stderr("parse cmd failed! " + Utils.formatErr(e) + " ... type `help` to show the help message");
            return;
        }
        c.run(new ResultCallback(line));
    }

    private static class ResultCallback extends Callback<CmdResult, Throwable> {
        private final String line;

        private ResultCallback(String line) {
            this.line = line;
        }

        @Override
        protected void onSucceeded(CmdResult resp) {
            if (!resp.strResult.trim().isEmpty()) {
                stdout(resp.strResult.trim());
            } else {
                stdout("(done)");
            }
        }

        @Override
        protected void onFailed(Throwable err) {
            String msg = "command `" + line + "` failed! " + Utils.formatErr(err);
            stderr(msg);
        }
    }

    private static class StrResultCallback extends Callback<String, Throwable> {
        private final String line;

        private StrResultCallback(String line) {
            this.line = line;
        }

        @Override
        protected void onSucceeded(String resp) {
            if (!resp.trim().isEmpty()) {
                stdout(resp.trim());
            } else {
                stdout("(done)");
            }
        }

        @Override
        protected void onFailed(Throwable err) {
            String msg = "command `" + line + "` failed! " + Utils.formatErr(err);
            stderr(msg);
        }
    }
}
