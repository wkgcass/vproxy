package vproxy.app.controller;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.CmdResult;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.HelpCommand;
import vproxy.app.app.cmd.SystemCommand;
import vproxy.base.util.Callback;
import vproxy.base.util.Utils;

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
            if (line.startsWith("man ")) {
                stdoutSync(HelpCommand.manLine(line));
                continue;
            }
            if (line.equals("version")) {
                stdoutSync(Application.get().version);
                continue;
            }
            if (SystemCommand.isSystemCommand(line)) {
                handleSystemCommand(line);
            } else {
                handleCommand(line);
            }
        }
    }

    private static void printStarter() {
        System.out.print(STARTER);
    }

    private static final String DONE_STR = "(done)";

    private static void stdoutSync(String msg) {
        System.out.println(msg);
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

    private static void handleSystemCommand(String line) {
        SystemCommand.handleSystemCommand(line, new ResultCallback(line));
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
        // we record the thread where the callback object is created
        // then we compare it with the thread when
        // onSucceeded or onFailed is called
        // which will tell whether it is an async call
        private final Thread startThread;

        private ResultCallback(String line) {
            this.line = line;
            this.startThread = Thread.currentThread();
        }

        @Override
        protected void onSucceeded(CmdResult resp) {
            boolean isAsync = Thread.currentThread() != startThread;
            String msg;
            if (!resp.strResult.trim().isEmpty()) {
                msg = resp.strResult.trim();
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
