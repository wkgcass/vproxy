package net.cassite.vproxy.component.app;

import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.util.Callback;
import net.cassite.vproxy.util.Utils;

import java.util.Scanner;

public class StdIOController {
    private static final String STARTER = "> ";

    @SuppressWarnings("InfiniteLoopStatement")
    public void start() {
        while (true) {
            Scanner scanner = new Scanner(System.in);
            System.out.print(STARTER);
            String line = scanner.nextLine().trim();
            if (line.isEmpty())
                continue;
            if (line.equals("h") || line.equals("help")) {
                stdout(Command.helpString());
                continue;
            }
            if (line.startsWith("System call:")) {
                handleSystemCall(line);
            } else {
                handleCommand(line);
            }
        }
    }

    private static void stdout(String msg) {
        System.out.println(msg);
        System.out.print(STARTER);
    }

    private static void stderr(String err) {
        System.err.println(err);
        System.err.println(STARTER);
    }

    private static void handleSystemCall(String line) {
        String cmd = line.substring("System call:".length()).trim();
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
                        Shutdown.load(filename.toString(), new ResultCallback(line));
                    } catch (Exception e) {
                        stderr("got exception when do pre-loading: " + Utils.formatErr(e));
                    }
                    break;
                }
                stderr("unknown system call `" + cmd + "`");
        }
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

    private static class ResultCallback extends Callback<String, Throwable> {
        private final String line;

        private ResultCallback(String line) {
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
