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
                stdoutSync(Command.helpString());
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

    private static final String DONE_STR = "(done)";

    private static void doneSync() {
        stdoutSync(DONE_STR);
    }

    private static void stdoutSync(String msg) {
        System.out.println(msg);
    }

    private static void doneAsync() {
        stdoutAsync(DONE_STR);
    }

    private static void stdoutAsync(String msg) {
        stdoutSync(msg);
        printStarter();
    }

    private static void stderrSync(String err) {
        System.out.println("\033[0;31m" + err + "\033[0m");
    }

    private static void stderrAsync(String err) {
        stderrSync(err);
        printStarter();
    }

    private static void handleSystemCall(String line) {
        String cmd = line.substring("System call:".length()).trim();
        outswitch:
        switch (cmd) {
            case "help":
                stdoutSync(Command.helpString());
                break;
            case "shutdown":
                Shutdown.shutdown();
                break;
            default:
                if (cmd.startsWith("load ")) {
                    String[] split = cmd.split(" ");
                    if (split.length <= 1) {
                        stderrSync("invalid system call for `load`: should specify a file name to load");
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
                        stderrSync("got exception when do pre-loading: " + Utils.formatErr(e));
                    }
                    break;
                } else if (cmd.startsWith("save ")) {
                    String[] split = cmd.split(" ");
                    if (split.length <= 1) {
                        stderrSync("invalid system call for `save`: should specify a file name to save");
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
                        stderrSync("got exception when saving: " + Utils.formatErr(e));
                    }
                    break;
                } else if (cmd.startsWith("add ")) {
                    String[] arr = cmd.split(" ");
                    if (arr.length < 2) {
                        stderrSync("invalid add command");
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
                        stderrSync("invalid remove command");
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
                        stderrSync("invalid list command");
                        break;
                    }
                    switch (arr[1]) {
                        case "resp-controller":
                            if (arr.length == 2) {
                                handleListController(false);
                                break outswitch;
                            }
                        case "config":
                            if (arr.length == 2) {
                                handleListConfig();
                                break outswitch;
                            }
                    }
                } else if (cmd.startsWith("list-detail ")) {
                    String[] arr = cmd.split(" ");
                    if (arr.length < 2) {
                        stderrSync("invalid list-detail command");
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
                stderrSync("unknown or invalid system call `" + cmd + "`");
        }
    }

    private static void handleListConfig() {
        stdoutSync(Shutdown.currentConfig());
    }

    private static void handleAddRespController(String[] arr) {
        Command cmd;
        try {
            cmd = Command.statm(Arrays.asList(arr));
        } catch (Exception e) {
            stderrSync("invalid system call: " + Utils.formatErr(e));
            return;
        }
        if (!cmd.args.containsKey(Param.addr)) {
            stderrSync("missing address");
            return;
        }
        if (!cmd.args.containsKey(Param.pass)) {
            stderrSync("missing password");
            return;
        }
        try {
            AddrHandle.check(cmd);
        } catch (Exception e) {
            stderrSync("invalid");
            return;
        }

        InetSocketAddress addr = AddrHandle.get(cmd);
        byte[] pass = cmd.args.get(Param.pass).getBytes();

        // start
        try {
            Application.get().respControllerHolder.add(cmd.resource.alias, addr, pass);
        } catch (AlreadyExistException e) {
            stderrSync("the RESPController is already started");
        } catch (IOException e) {
            stderrSync("got exception when starting RESPController: " + Utils.formatErr(e));
        }
        doneSync();
    }

    private static void handleRemoveController(String[] arr) {
        try {
            Application.get().respControllerHolder.removeAndStop(arr[2]);
        } catch (NotFoundException e) {
            stderrSync("not found");
        }
        doneSync();
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
        stdoutSync(sb.toString());
    }

    private static void handleCommand(String line) {
        Command c;
        try {
            c = Command.parseStrCmd(line);
        } catch (Exception e) {
            stderrSync("parse cmd failed! " + Utils.formatErr(e) + " ... type `help` to show the help message");
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
                stdoutAsync(resp.strResult.trim());
            } else {
                doneAsync();
            }
        }

        @Override
        protected void onFailed(Throwable err) {
            String msg = "command `" + line + "` failed! " + Utils.formatErr(err);
            stderrAsync(msg);
        }
    }

    private static class StrResultCallback extends Callback<String, Throwable> {
        private final String line;
        // we record the thread where the callback object is created
        // then we compare it with the thread when
        // onSucceeded or onFailed is called
        // which will tell whether it is an async call
        private final Thread startThread;

        private StrResultCallback(String line) {
            this.line = line;
            this.startThread = Thread.currentThread();
        }

        @Override
        protected void onSucceeded(String resp) {
            boolean isAsync = Thread.currentThread() != startThread;
            String msg;
            if (!resp.trim().isEmpty()) {
                msg = resp.trim();
            } else {
                msg = DONE_STR;
            }
            if (isAsync) {
                stdoutAsync(msg);
            } else {
                stdoutSync(msg);
            }
        }

        @Override
        protected void onFailed(Throwable err) {
            boolean isAsync = Thread.currentThread() != startThread;
            String msg = "command `" + line + "` failed! " + Utils.formatErr(err);
            if (isAsync) {
                stderrAsync(msg);
            } else {
                stderrSync(msg);
            }
        }
    }
}
